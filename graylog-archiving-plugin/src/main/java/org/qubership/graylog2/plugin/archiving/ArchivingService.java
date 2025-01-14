package org.qubership.graylog2.plugin.archiving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.qubership.graylog2.plugin.utils.GraylogProcessor;
import org.qubership.graylog2.plugin.utils.ParametersProcessor;
import org.qubership.graylog2.plugin.utils.TimeUnitProcessor;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Cat;
import io.searchbox.snapshot.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class ArchivingService {

    private static final Logger log = LoggerFactory.getLogger(ArchivingService.class);

    private static final String DEFAULT_S3CLIENT = "default";

    private final String snapshotDirectory = System.getenv("GRAYLOG_SNAPSHOT_DIRECTORY");

    private static final String directoriesFile = "/usr/share/graylog/data/config/directories.json";

    private Map<String, String> directories;

    private final GraylogProcessor graylogProcessor;

    public final ParametersProcessor parametersProcessor;

    private final TimeUnitProcessor timeUnitProcessor;

    private final JestClient jestClient;

    private final Map<String, ArchiveInfo> processes;

    private final ExecutorService executorService;

    private final Scheduler scheduler;

    @Inject
    public ArchivingService(GraylogProcessor graylogProcessor,
                            ParametersProcessor parametersProcessor, TimeUnitProcessor timeUnitProcessor, @Named("ArchivingJestClient") JestClient jestClient) throws SchedulerException {
        this.graylogProcessor = graylogProcessor;
        this.parametersProcessor = parametersProcessor;
        this.timeUnitProcessor = timeUnitProcessor;
        this.processes = new ConcurrentHashMap<>();
        this.directories = readDirectoriesFile();
        this.executorService = Executors.newFixedThreadPool(3);
        this.jestClient = jestClient;
        this.scheduler = new StdSchedulerFactory().getScheduler();
        this.scheduler.start();
    }

    public static JSONObject parseJSONFile(String filename) throws JSONException, IOException {
        String content = new String(Files.readAllBytes(Paths.get(filename)));
        return new JSONObject(content);
    }

    public static HashMap<String, String> readDirectoriesFile() {
        HashMap<String, String> map = new HashMap<>();
        try {
            JSONObject jsonObject = parseJSONFile(directoriesFile);
            ObjectMapper mapper = new ObjectMapper();
            map = mapper.readValue(jsonObject.toString(), HashMap.class);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    public ArchiveInfo getArchiveProcessInfo(String id) {
        return processes.get(id);
    }

    public String resolvePath(String storageId) {
        String path = directories.get(storageId);
        if (path.contains("/"))
            return path.substring(path.lastIndexOf('/') + 1);
        return path;
    }

    private String getSnapshotStatus(String storageId, String archiveName) throws IOException {
        GetSnapshot status = new GetSnapshot
                .Builder(resolvePath(storageId))
                .addSnapshot(archiveName)
                .build();

        return jestClient.execute(status).getJsonString();
    }

    private CreateSnapshot createSnapshot(String storageId, String archiveName, HashSet<String> indices) {
        CreateSnapshot.Builder snapshotBuilder = new CreateSnapshot
                .Builder(resolvePath(storageId), archiveName);
        return snapshotBuilder
                .settings(ImmutableMap.of("indices", indices))
                .waitForCompletion(true)
                .build();
    }

    public String archive(String storageId, String archiveName, HashSet<String> indices) {
        if (!directories.containsKey(storageId)) {
            log.info("Plugin doesn't contain settings for storageId: " + storageId + ". Create it as FS");
            addFSSettings(storageId, null);
        }
        if (!graylogProcessor.checkExisting(archiveName)) {
            CreateSnapshot snapshot = createSnapshot(storageId, archiveName, indices);
            String id = UUID.randomUUID().toString();
            ArchiveInfo archiveInfo = new ArchiveInfo(id, new Date());
            archiveInfo.setStatus("Starting archive procedure");
            processes.put(id, archiveInfo);
            executorService.submit(() -> {
                try {
                    archiveInfo.setStatus("Loading data from Elasticsearch");
                    JestResult result = jestClient.execute(snapshot);
                    if (result.getResponseCode() == 200) {
                        String snapshotStatus = getSnapshotStatus(storageId, archiveName);
                        String response = getArchiveInfo(storageId, archiveName);
                        graylogProcessor.createInfoFile(archiveName, response);
                        archiveInfo.setStatus("Success");
                        archiveInfo.setResult(snapshotStatus);
                    } else {
                        archiveInfo.setStatus("Failed");
                        archiveInfo.setResult(result.getJsonString());
                    }
                } catch (IOException | RuntimeException e) {
                    log.error(e.getMessage(), e);
                    archiveInfo.setStatus("Failed");
                    archiveInfo.setResult(e.getMessage());
                }
            });
            return id;
        } else return "Archive with name " + archiveName + " already exists!";
    }

    public String readInfoFile(String archiveName) {
        return graylogProcessor.getArchiveInfo(archiveName);
    }

    private String getArchiveInfo(String storageId, String archiveName) throws IOException {
        SnapshotStatus status = new SnapshotStatus
                .Builder(resolvePath(storageId))
                .addSnapshot(archiveName)
                .build();
        return jestClient.execute(status).getJsonString();
    }

    public String restore(String storageId, String archiveName) {
        String uuid = UUID.randomUUID().toString();
        ArchiveInfo restoreInfo = new ArchiveInfo(uuid, new Date());
        restoreInfo.setStatus("Starting restore procedure");
        log.info("Starting restore procedure");
        processes.put(uuid, restoreInfo);
        executorService.submit(() -> {
            try {
                graylogProcessor.prepareEnvironment();
                List<String> indices = graylogProcessor.getIndices(getSnapshotStatus(storageId, archiveName));
                int id = graylogProcessor.getActiveWriteIndexNumber();
                JSONObject result = new JSONObject();
                for (String index : indices) {
                    restoreInfo.setStatus("Restoring: " + index);
                    Map<String, Object> settings = ImmutableMap.<String, Object>builder()
                            .put("indices", index)
                            .put("rename_pattern", "(.+)")
                            .put("rename_replacement", "restored_".concat(String.valueOf(++id)))
                            .build();
                    RestoreSnapshot snapshot = new RestoreSnapshot
                            .Builder(resolvePath(storageId), archiveName)
                            .settings(settings)
                            .build();
                    JestResult execute = jestClient.execute(snapshot);
                    result.put(index, execute.getJsonString());
                }
                graylogProcessor.waitForCompletion("restored_" + id);
                restoreInfo.setStatus("Success");
                restoreInfo.setResult(result.toString());
            } catch (IOException | RuntimeException | InterruptedException e) {
                log.error(e.getMessage(), e);
                restoreInfo.setStatus("Failed");
                restoreInfo.setResult(e.getMessage());
            }
        });
        return uuid;
    }

    public String delete(String storageId, String archiveName) {
        String uuid = UUID.randomUUID().toString();
        ArchiveInfo deleteInfo = new ArchiveInfo(uuid, new Date());
        deleteInfo.setStatus("Starting delete procedure");
        log.info("Starting delete procedure");
        processes.put(uuid, deleteInfo);
        executorService.submit(() -> {
            try {
                deleteInfo.setStatus("Waiting for deletion data from elasticsearch");
                DeleteSnapshot snapshot = new DeleteSnapshot.Builder(resolvePath(storageId), archiveName).build();
                jestClient.execute(snapshot).getJsonString();
                deleteInfo.setStatus("Waiting for deletion data from volume");
                String result = graylogProcessor.deleteArchive(archiveName);
                deleteInfo.setStatus("Success");
                deleteInfo.setResult(result);
            } catch (IOException | RuntimeException e) {
                log.error(e.getMessage(), e);
                deleteInfo.setStatus("Failed");
                deleteInfo.setResult(e.getMessage());
            }
        });
        return uuid;
    }

    public List<String> getIndicesByMasks(ShortIndex[] lst, List<String> masks) {
        List<String> toArchive = new ArrayList<>();
        for (ShortIndex i : lst) {
            for (String m : masks) {
                if (i.getName().startsWith(m)) {
                    toArchive.add(i.getName());
                    break;
                }
            }
        }
        return toArchive;
    }

    public List<String> getIndicesByPeriod(ShortIndex[] lst, String period) {
        List<String> toArchive = new ArrayList<>();
        long currentTime = new Date().getTime();
        long searchedTime = currentTime - timeUnitProcessor.toLong(period);
        for (ShortIndex i : lst) {
            if (i.getCreationDate() > searchedTime) {
                toArchive.add(i.getName());
            }
        }
        return toArchive;
    }

    public List<String> getIndices(String period, List<String> masks) {
        if ((period == null) && (masks.isEmpty()))
            return Collections.emptyList();
        List<String> toArchive = new ArrayList<>();
        try {
            JestResult execute = jestClient.execute(new Cat.IndicesBuilder().setParameter("h", "index,creation.date").build());
            ObjectMapper mapper = new ObjectMapper();
            ShortIndex[] lst = mapper.readValue(execute.getJsonString(), ShortIndex[].class);

            if ((period == null) && (!masks.isEmpty()))
                return getIndicesByMasks(lst, masks);
            if ((period != null) && (masks.isEmpty()))
                return getIndicesByPeriod(lst, period);

            long currentTime = new Date().getTime();
            long searchedTime = currentTime - timeUnitProcessor.toLong(period);
            for (ShortIndex i : lst) {
                if (i.getCreationDate() > searchedTime) {
                    for (String m : masks) {
                        if (i.getName().startsWith(m)) {
                            toArchive.add(i.getName());
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return toArchive;
    }

    public boolean schedule(String time, String storageId, String name, List<String> indices, String period, List<String> masks) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(ArchivingJob.class)
                .withIdentity(name)
                .build();
        job.getJobDataMap().put("time", time);
        job.getJobDataMap().put("storageId", storageId);
        job.getJobDataMap().put("name", name);
        job.getJobDataMap().put("masks", masks);
        job.getJobDataMap().put("indices", indices);
        job.getJobDataMap().put("service", this);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(name)
                .withSchedule(CronScheduleBuilder.cronSchedule(period))
                .build();
        log.info("Schedule job: " + name);
        scheduler.scheduleJob(job, trigger);
        return true;
    }

    public boolean unschedule(String name) throws SchedulerException {
        log.info("Unscedule job: " + name);
        scheduler.unscheduleJob(TriggerKey.triggerKey(name));
        return true;
    }

    public String createFSSnapshotDirectory(String name, String snapshotDir) {
        CreateSnapshotRepository repository = new CreateSnapshotRepository
                .Builder(name).settings(ImmutableMap.of("type", "fs",
                        "settings", ImmutableMap.of("location", snapshotDir + name)))
                .build();
        try {
            return jestClient.execute(repository).getJsonString();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return e.getMessage();
        }
    }

    public String createS3SnapshotDirectory(String endpoint, String bucket, String region, String roleArn) {
        log.info("Create s3 repository with role: " + roleArn);
        CreateSnapshotRepository repository = new CreateSnapshotRepository
                .Builder(bucket).settings(ImmutableMap.of("type", "s3",
                "settings", ImmutableMap.of(
                        "client", DEFAULT_S3CLIENT,
                        "bucket", bucket,
                        "endpoint", endpoint,
                        "region", region,
                        "role_arn", roleArn))).build();
        try {
            return jestClient.execute(repository).getJsonString();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return e.getMessage();
        }
    }

    public String createS3SnapshotDirectory(String endpoint, String bucket, String region) {
        CreateSnapshotRepository repository = new CreateSnapshotRepository
                .Builder(bucket).settings(ImmutableMap.of("type", "s3",
                "settings", ImmutableMap.of(
                        "client", DEFAULT_S3CLIENT,
                        "bucket", bucket,
                        "endpoint", endpoint,
                        "region", region))).build();
        try {
            return jestClient.execute(repository).getJsonString();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return e.getMessage();
        }
    }

    public void writeDirectoriesFile(String stream, String name) throws IOException {
        directories.put(stream, name);
        JSONObject jsonObject = new JSONObject(directories);
        try (OutputStreamWriter file = new OutputStreamWriter(new FileOutputStream(directoriesFile), StandardCharsets.UTF_8)) {
            file.write(jsonObject.toString());
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String addFSSettings(String name, String snapshotDir) {
        try {
            if ((snapshotDir == null) || (snapshotDir.isEmpty()))
                snapshotDir = snapshotDirectory;
            String response = createFSSnapshotDirectory(name, snapshotDir);
            writeDirectoriesFile(name, snapshotDir + name);
            return response;
        } catch (IOException e) {
            log.error(e.getMessage());
            return e.getMessage();
        }
    }

    public String addS3Settings(String name, String endpoint, String bucket, String region, String roleArn) {
        try {
            String response = "";
            if (roleArn != null)
                response = createS3SnapshotDirectory(endpoint, bucket, region, roleArn);
            else response = createS3SnapshotDirectory(endpoint, bucket, region);
            log.info("Response: " + response);
            writeDirectoriesFile(name, bucket);
            return response;
        } catch (IOException e) {
            log.error(e.getMessage());
            return e.getMessage();
        }
    }
}