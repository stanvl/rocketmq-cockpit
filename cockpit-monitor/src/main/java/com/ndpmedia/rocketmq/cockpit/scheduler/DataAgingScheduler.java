package com.ndpmedia.rocketmq.cockpit.scheduler;

import com.ndpmedia.rocketmq.cockpit.mybatis.mapper.CockpitMessageMapper;
import com.ndpmedia.rocketmq.cockpit.mybatis.mapper.ConsumeProgressMapper;
import com.ndpmedia.rocketmq.cockpit.mybatis.mapper.LoginMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.List;

/**
 * Created by robert on 2015/6/9.
 * data aging scheduler.
 * 1.consume progress 、message flow
 * 2.login message
 */
@Component
public class DataAgingScheduler {
    private Logger logger = LoggerFactory.getLogger(DataAgingScheduler.class);

    @Autowired
    private LoginMapper loginMapper;

    @Autowired
    private CockpitMessageMapper cockpitMessageMapper;

    @Autowired
    private ConsumeProgressMapper consumeProgressMapper;

    /**
     * schedule:delete old comsume progress 、message flow records.
     * period:one hour(the first second of an hour)
     * span: records old than one day
     */
    @Scheduled(cron = "0 0 * * * *")
    public void deleteDeprecatedData() {
        logger.info("Start to clean deprecated data");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, -24);
        int numberOfRecordsDeleted = consumeProgressMapper.bulkDelete("", calendar.getTime());
        logger.info("Deleted " + numberOfRecordsDeleted + " consume progress records.");
//        numberOfRecordsDeleted = cockpitMessageMapper.bulkDelete(calendar.getTime());
//        logger.info("Deleted " + numberOfRecordsDeleted + " message flow records.");

        calendar.add(Calendar.HOUR_OF_DAY, -48);

        List<Integer> IDs = consumeProgressMapper.groupTableIDS();
        for(int id : IDs){
            try {
                numberOfRecordsDeleted = consumeProgressMapper.bulkDelete("_" + id, calendar.getTime());
                logger.info("Deleted " + numberOfRecordsDeleted + " in " + id + " consume progress records.");
            }catch (Exception e){
                System.out.println("[MONITOR][OLD DATA DELETE] some table already deleted.");
            }
        }
    }

    /**
     * schedule: delete login message
     * period:30 second
     * span:old than 30 minutes
     */
    @Scheduled(fixedRate = 30000)
    public void deleteDeprecatedLoginData() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -30);
        loginMapper.delete(calendar.getTime());
    }
}
