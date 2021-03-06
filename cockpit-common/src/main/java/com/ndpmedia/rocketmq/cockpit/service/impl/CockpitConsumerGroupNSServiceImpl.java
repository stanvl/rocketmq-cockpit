package com.ndpmedia.rocketmq.cockpit.service.impl;

import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.protocol.body.Connection;
import com.alibaba.rocketmq.common.protocol.body.ConsumerConnection;
import com.alibaba.rocketmq.common.protocol.body.ConsumerRunningInfo;
import com.alibaba.rocketmq.common.subscription.SubscriptionGroupConfig;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import com.alibaba.rocketmq.tools.command.CommandUtil;
import com.ndpmedia.rocketmq.cockpit.model.ConsumerGroup;
import com.ndpmedia.rocketmq.cockpit.mybatis.mapper.ConsumerGroupMapper;
import com.ndpmedia.rocketmq.cockpit.service.CockpitConsumerGroupNSService;
import com.ndpmedia.rocketmq.cockpit.util.Helper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Service("cockpitConsumerGroupNSService")
public class CockpitConsumerGroupNSServiceImpl implements CockpitConsumerGroupNSService {

    @Autowired
    private ConsumerGroupMapper consumerGroupMapper;

    @Override
    public boolean update(ConsumerGroup consumerGroup) {
        DefaultMQAdminExt defaultMQAdminExt = new DefaultMQAdminExt();
        defaultMQAdminExt.setInstanceName(Helper.getInstanceName());
        try {
            defaultMQAdminExt.start();
            SubscriptionGroupConfig subscriptionGroupConfig = wrap(consumerGroup);
            if (null != consumerGroup.getBrokerAddress()) {
                defaultMQAdminExt.createAndUpdateSubscriptionGroupConfig(consumerGroup.getBrokerAddress(), subscriptionGroupConfig);
            } else {
                Set<String> masterSet = CommandUtil
                        .fetchMasterAddrByClusterName(defaultMQAdminExt, consumerGroup.getClusterName());

                if (null != masterSet && !masterSet.isEmpty()) {
                    for (String brokerAddress : masterSet) {
                        defaultMQAdminExt.createAndUpdateSubscriptionGroupConfig(brokerAddress, subscriptionGroupConfig);
                    }
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            defaultMQAdminExt.shutdown();
        }
        return true;
    }

    @Override
    public boolean clear(ConsumerGroup consumerGroup) {
        DefaultMQAdminExt defaultMQAdminExt = new DefaultMQAdminExt();
        defaultMQAdminExt.setInstanceName(Helper.getInstanceName());
        try {
            defaultMQAdminExt.start();
            if (null != consumerGroup.getBrokerAddress() && !consumerGroup.getBrokerAddress().isEmpty()){
                defaultMQAdminExt.deleteSubscriptionGroup(consumerGroup.getBrokerAddress(), consumerGroup.getGroupName());
            } else {
                Set<String> masterSet = CommandUtil
                        .fetchMasterAddrByClusterName(defaultMQAdminExt, consumerGroup.getClusterName());

                if (null != masterSet && !masterSet.isEmpty()) {
                    for (String brokerAddress : masterSet) {
                        defaultMQAdminExt.deleteSubscriptionGroup(brokerAddress, consumerGroup.getGroupName());
                    }
                }
            }
        }catch (Exception e){
            System.out.println("DELETE CONSUMER GROUP ON BROKER FIALED!" + e);
            return false;
        }finally {
            defaultMQAdminExt.shutdown();
        }

        return true;
    }

    @Override
    public Set<String>  getGroups(DefaultMQAdminExt defaultMQAdminExt) {
        Set<String> consumerGroups = new HashSet<>();
        int flag = 0;
        while (flag++ < 5) {
            try {
                Set<String> topics = defaultMQAdminExt.fetchAllTopicList().getTopicList();
                for (String topic : topics) {
                    if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX) || topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX))
                        consumerGroups.add(topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX) ? topic.replace(MixAll.RETRY_GROUP_TOPIC_PREFIX, "") :
                                topic.replace(MixAll.DLQ_GROUP_TOPIC_PREFIX, ""));
                }

                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return consumerGroups;
    }

    @Override
    public SubscriptionGroupConfig getGroupConfig(DefaultMQAdminExt defaultMQAdminExt, String groupName) {
        try{
            ConsumerConnection consumerConnection = defaultMQAdminExt.examineConsumerConnectionInfo(groupName);
            if (null != consumerConnection){
                Iterator<Connection> conns = consumerConnection.getConnectionSet().iterator();
                while (conns.hasNext()){
                    Connection connection = conns.next();
                    String clientId = connection.getClientId();
                    if (null != clientId && !clientId.isEmpty()){
                        ConsumerRunningInfo info = defaultMQAdminExt.getConsumerRunningInfo(groupName, clientId, true);
                        if (null != info)
                            return null;
                    }
                }
            }
        }catch (Exception e){
            System.out.println("try to get SubscriptionGroupConfig failed! group :" + groupName + e);
        }
        return null;
    }

    private SubscriptionGroupConfig wrap(ConsumerGroup consumerGroup) {
        SubscriptionGroupConfig subscriptionGroupConfig = new SubscriptionGroupConfig();
        subscriptionGroupConfig.setBrokerId(consumerGroup.getBrokerId());
        subscriptionGroupConfig.setConsumeBroadcastEnable(consumerGroup.isConsumeBroadcastEnable());
        subscriptionGroupConfig.setConsumeEnable(consumerGroup.isConsumeEnable());
        subscriptionGroupConfig.setConsumeFromMinEnable(consumerGroup.isConsumeFromMinEnable());
        subscriptionGroupConfig.setGroupName(consumerGroup.getGroupName());
        subscriptionGroupConfig.setRetryMaxTimes(consumerGroup.getRetryMaxTimes());
        subscriptionGroupConfig.setRetryQueueNums(consumerGroup.getRetryQueueNum());
        subscriptionGroupConfig.setWhichBrokerWhenConsumeSlowly(consumerGroup.getWhichBrokerWhenConsumeSlowly());
        return subscriptionGroupConfig;
    }
}
