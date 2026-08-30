package com.example.integration.scheduler;

import com.example.integration.model.enums.ScheduleType;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@DisallowConcurrentExecution
public class QuartzIntegrationEnqueueJob implements Job {

    static final String INTEGRATION_ID = "integrationId";
    static final String SCHEDULE_TYPE = "scheduleType";
    static final String SCHEDULE_INDEX = "scheduleIndex";

    @Autowired
    private QuartzScheduleService quartzScheduleService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getMergedJobDataMap();
            Long integrationId = dataMap.getLong(INTEGRATION_ID);
            ScheduleType scheduleType = ScheduleType.valueOf(dataMap.getString(SCHEDULE_TYPE));
            int scheduleIndex = dataMap.getInt(SCHEDULE_INDEX);
            LocalDateTime triggerTime = LocalDateTime.ofInstant(
                    context.getScheduledFireTime().toInstant(),
                    ZoneId.systemDefault());

            quartzScheduleService.enqueueTriggeredSchedule(integrationId, scheduleType, scheduleIndex, triggerTime);
        } catch (Exception ex) {
            log.error("Quartz enqueue job failed message={}", ex.getMessage(), ex);
            throw new JobExecutionException(ex);
        }
    }
}
