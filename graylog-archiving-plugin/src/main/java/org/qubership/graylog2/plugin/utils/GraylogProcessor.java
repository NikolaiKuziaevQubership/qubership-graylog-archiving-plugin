package org.qubership.graylog2.plugin.utils;

import com.google.common.collect.ImmutableMap;
import io.searchbox.client.JestClient;
import io.searchbox.core.Cat;
import io.searchbox.core.CatResult;
import org.graylog2.database.NotFoundException;
import org.graylog2.indexer.IndexSet;
import org.graylog2.indexer.IndexSetRegistry;
import org.graylog2.indexer.indexset.IndexSetConfig;
import org.graylog2.indexer.indexset.IndexSetService;
import org.graylog2.indexer.retention.strategies.DeletionRetentionStrategyConfig;
import org.graylog2.indexer.rotation.strategies.SizeBasedRotationStrategyConfig;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.database.ValidationException;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.shared.users.Role;
import org.graylog2.shared.users.UserService;
import org.graylog2.streams.StreamService;
import org.graylog2.users.RoleService;
import org.joda.time.Duration;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class GraylogProcessor {

    private static final String RESTORED_INDEX_PREFIX = "restored";

    private static final String PATH = "/usr/share/graylog/data/archives/";

    private static final String INDICES_DIR = "indices";

    private static final String ROLE = "AuditViewer";

    private static final String INDEX_SET_NAME = "Restored index set";

    private static final String STREAM_NAME = "Restored logs";

    private static final Logger log = LoggerFactory.getLogger(GraylogProcessor.class);

    private final IndexSetService indexSetService;

    private final IndexSetRegistry indexSetRegistry;

    private final StreamService streamService;

    private final UserService userService;

    private final RoleService roleService;

    private final FileProcessor fileProcessor;

    private final JestClient jestClient;

    @Inject
    public GraylogProcessor(IndexSetRegistry indexSetRegistry,
                            IndexSetService indexSetService,
                            StreamService streamService,
                            UserService userService,
                            RoleService roleService,
                            FileProcessor fileProcessor,
                            @Named("ArchivingJestClient") JestClient jestClient) {
        this.indexSetRegistry = indexSetRegistry;
        this.indexSetService = indexSetService;
        this.streamService = streamService;
        this.userService = userService;
        this.roleService = roleService;
        this.fileProcessor = fileProcessor;
        this.jestClient = jestClient;
    }

    public int getActiveWriteIndexNumber() {
        Set<IndexSet> all = indexSetRegistry.getAll();
        for (IndexSet entry : all) {
            if (entry.getConfig().indexPrefix().equals(RESTORED_INDEX_PREFIX)) {
                Optional<IndexSet> optionalIndexSet = indexSetRegistry.get(entry.getConfig().id());
                if (optionalIndexSet.isPresent()) {
                    IndexSet indexSet = optionalIndexSet.get();
                    waitIndexSetIsUp(indexSet);
                    String id = indexSet.getActiveWriteIndex();
                    if (id != null) {
                        return Integer.parseInt(indexSet.getActiveWriteIndex().split("_")[1]);
                    }
                }
            }
        }
        throw new RuntimeException("Can't find active write index number");
    }

    private void waitIndexSetIsUp(IndexSet indexSet) {
        while (!indexSet.isUp()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public List<String> getIndices(String snapshotStatus) {
        try {
            return new JSONObject(snapshotStatus)
                    .getJSONArray("snapshots")
                    .getJSONObject(0)
                    .getJSONArray(INDICES_DIR)
                    .toList()
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } catch (JSONException e) {
            log.error("Can't get indices from JSON: " + e.getMessage(), e);
            throw new RuntimeException("Error during getting indices from JSON." + "\n" +
                    "Reason: " + e.getMessage(), e);
        }
    }

    public void waitForCompletion(String index) throws InterruptedException, IOException {
        Cat cat = new Cat.IndicesBuilder().addIndex(index).build();
        CatResult result = jestClient.execute(cat);
        String health = new JSONArray(result.getJsonString()).getJSONObject(0).getString("health");
        while (!"green".equals(health)) {
            TimeUnit.SECONDS.sleep(5);
            result = jestClient.execute(cat);
            health = new JSONArray(result.getJsonString()).getJSONObject(0).getString("health");
            if (health.equals("red")) {
                throw new RuntimeException("Health status of " + index + " is red!");
            }
        }
    }

    public void prepareEnvironment() {
        Optional<String> optionalIndexSetId = getIndexSetId();
        String indexSetId = optionalIndexSetId.orElseGet(this::createIndexSet);
        Optional<String> optionalStreamId = getStreamId();
        String streamId = optionalStreamId.orElseGet(() -> createStream(indexSetId));
        if (roleExists()) {
            updateRole(streamId);
        }
    }

    public boolean checkExisting(String archiveName) {
        return fileProcessor.checkFileExisting(PATH, archiveName);
    }

    public String deleteArchive(String archiveName) {
        if (checkExisting(archiveName)) {
            try {
                Files.delete(Paths.get(PATH, archiveName + ".json"));
                return "Success";
            } catch (IOException e) {
                log.error("Error during deleting: " + e.getMessage(), e);
                throw new RuntimeException("Error during deleting." + "\n" +
                        "Reason: " + e.getMessage(), e);
            }
        }
        return "Archive with name " + archiveName + " doesn't exist!";
    }

    public String getArchiveInfo(String archiveName) {
        return fileProcessor.readInfoFile(PATH, archiveName);
    }

    private Optional<String> getIndexSetId() {
        Set<IndexSet> all = indexSetRegistry.getAll();
        for (IndexSet indexSet : all) {
            if (indexSet.getConfig().title().equals(INDEX_SET_NAME)) {
                String id = indexSet.getConfig().id();
                if (id != null) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> getStreamId() {
        List<Stream> all = streamService.loadAll();
        for (Stream stream : all) {
            if (stream.getTitle().equals(STREAM_NAME)) {
                return Optional.of(stream.getId());
            }
        }
        return Optional.empty();
    }

    private boolean roleExists() {
        return roleService.exists(ROLE);
    }

    private void updateRole(String restoredStreamId) {
        try {
            Role auditViewer = roleService.load(ROLE);
            Set<String> permissions = auditViewer.getPermissions();
            String newPermission = "streams:read:" + restoredStreamId;
            if (!permissions.contains(newPermission)) {
                permissions.add(newPermission);
                auditViewer.setPermissions(permissions);
            }
            roleService.save(auditViewer);
        } catch (NotFoundException | ValidationException e) {
            log.error("Error during updating role: " + e.getMessage(), e);
            throw new RuntimeException("Error during updating role." + "\n" +
                    "Reason: " + e.getMessage(), e);
        }
    }

    private String createStream(String restoredIndexSetId) {
        Map<String, Object> settings = ImmutableMap.<String, Object>builder()
                .put("matching_type", "AND")
                .put("description", "Restored log messages from archiving plugin")
                .put("title", STREAM_NAME)
                .put("index_set_id", restoredIndexSetId)
                .put("creator_user_id", Objects.requireNonNull(userService.load("admin")).getId())
                .put("disabled", false)
                .put("created_at", Tools.nowUTC())
                .build();
        Stream stream = streamService.create(settings);
        try {
            streamService.save(stream);
            return stream.getId();
        } catch (ValidationException e) {
            log.error("Error during creating stream: " + e.getMessage(), e);
            throw new RuntimeException("Error during creating stream." + "\n" +
                    "Reason: " + e.getMessage(), e);
        }
    }

    private String createIndexSet() {
        SizeBasedRotationStrategyConfig rotationStrategyConfig = SizeBasedRotationStrategyConfig
                .create("org.graylog2.indexer.rotation.strategies.SizeBasedRotationStrategyConfig",
                        1073741824);
        DeletionRetentionStrategyConfig retentionStrategyConfig = DeletionRetentionStrategyConfig
                .create("org.graylog2.indexer.retention.strategies.DeletionRetentionStrategyConfig",
                        100);
        IndexSetConfig indexSetConfig = IndexSetConfig
                .builder()
                .title(INDEX_SET_NAME)
                .indexPrefix("restored")
                .shards(4)
                .replicas(0)
                .rotationStrategyClass("org.graylog2.indexer.rotation.strategies.SizeBasedRotationStrategy")
                .rotationStrategy(rotationStrategyConfig)
                .retentionStrategyClass("org.graylog2.indexer.retention.strategies.DeletionRetentionStrategy")
                .retentionStrategy(retentionStrategyConfig)
                .creationDate(ZonedDateTime.now())
                .indexAnalyzer("standard")
                .indexTemplateName("restored")
                .indexOptimizationMaxNumSegments(1)
                .indexOptimizationDisabled(false)
                .isWritable(true)
                .fieldTypeRefreshInterval(new Duration(5000))
                .build();
        return indexSetService.save(indexSetConfig).id();
    }

    public void createInfoFile(String archiveName, String archiveInfo) {
        fileProcessor.createInfoFile(PATH, archiveName, archiveInfo);
    }
}
