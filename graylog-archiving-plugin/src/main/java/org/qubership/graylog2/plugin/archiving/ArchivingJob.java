package org.qubership.graylog2.plugin.archiving;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

public class ArchivingJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ArchivingService.class);

    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String time = dataMap.getString("time");
        String archiveName = dataMap.getString("name");
        String storageId = dataMap.getString("storageId");
        List<String> indices = (List<String>) dataMap.get("indices");
        List<String> masks = (List<String>) dataMap.get("masks");
        ArchivingService service = (ArchivingService) dataMap.get("service");

        Timestamp ts = new Timestamp(new Date().getTime());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String name = archiveName + "_" + formatter.format(ts);
        List<String> indicesByParams = service.getIndices(time, masks);
        HashSet<String> mergedIndices = new HashSet<>();
        mergedIndices.addAll(indices);
        mergedIndices.addAll(indicesByParams);
        log.info("Try to create archive with name: " + name + " for indices " + mergedIndices);
        log.info("Process id for created archive: " + service.archive(storageId, name, mergedIndices));
    }
}
