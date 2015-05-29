package com.ndpmedia.rocketmq.cockpit.scheduler.command;

import com.alibaba.rocketmq.client.exception.MQBrokerException;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.TopicConfig;
import com.alibaba.rocketmq.common.protocol.body.ClusterInfo;
import com.alibaba.rocketmq.common.protocol.body.TopicList;
import com.alibaba.rocketmq.common.protocol.route.BrokerData;
import com.alibaba.rocketmq.common.protocol.route.QueueData;
import com.alibaba.rocketmq.common.protocol.route.TopicRouteData;
import com.alibaba.rocketmq.remoting.RPCHook;
import com.alibaba.rocketmq.remoting.exception.RemotingException;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import com.alibaba.rocketmq.tools.command.SubCommand;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.util.*;

/**
 * Created by robert on 2015/5/29.
 */
public class SyncTopicCommand implements SubCommand {

    private Set<String> brokerList = new HashSet<>();

    @Override
    public String commandName() {
        return "syncTopic";
    }

    @Override
    public String commandDesc() {
        return " try to sync topic information ";
    }

    @Override
    public Options buildCommandlineOptions(Options options) {

        Option opt = new Option("b", "brokerAddr", true, "delete subscription group from which broker");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("c", "clusterName", true, "delete subscription group from which cluster");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("g", "groupName", true, "subscription group name");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }

    @Override
    public void execute(CommandLine commandLine, Options options, RPCHook rpcHook) {
        DefaultMQAdminExt adminExt = new DefaultMQAdminExt();
        adminExt.setInstanceName(Long.toString(System.currentTimeMillis()));
        try {
            adminExt.start();
            doList(adminExt);

            TopicList topics = adminExt.fetchAllTopicList();
            for (String topicName : topics.getTopicList()) {
                if (topicName.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX) || topicName.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX))
                    continue;

                TopicConfig topicConfig = getTopicConfig(adminExt, topicName);
                if (null != topicConfig)
                    rebuildTopicConfig(adminExt, topicConfig);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            adminExt.shutdown();
        }
    }

    private void doList(DefaultMQAdminExt defaultMQAdminExt) throws Exception {
        System.out.println("[sync topic] try to get broker list");
        ClusterInfo clusterInfoSerializeWrapper = defaultMQAdminExt.examineBrokerClusterInfo();

        Set<Map.Entry<String, Set<String>>> clusterSet =
                clusterInfoSerializeWrapper.getClusterAddrTable().entrySet();

        for (Map.Entry<String, Set<String>> next : clusterSet) {
            String clusterName = next.getKey();
            Set<String> brokerNameSet = new HashSet<String>();
            brokerNameSet.addAll(next.getValue());


            for (String brokerName : brokerNameSet) {
                BrokerData brokerData = clusterInfoSerializeWrapper.getBrokerAddrTable().get(brokerName);
                if (brokerData != null) {
                    Set<Map.Entry<Long, String>> brokerAddrSet = brokerData.getBrokerAddrs().entrySet();
                    Iterator<Map.Entry<Long, String>> itAddr = brokerAddrSet.iterator();

                    while (itAddr.hasNext()) {
                        Map.Entry<Long, String> next1 = itAddr.next();
                        brokerList.add(next1.getValue());
                    }
                }
            }
        }

        System.out.println("[sync topic] now we get broker list , size : " + brokerList.size() + " [] " + brokerList);
    }

    private TopicConfig getTopicConfig(DefaultMQAdminExt defaultMQAdminExt, String topic) throws RemotingException, MQClientException, InterruptedException {
        TopicConfig topicConfig = new TopicConfig();
        topicConfig.setTopicName(topic);

        TopicRouteData topicRouteData = defaultMQAdminExt.examineTopicRouteInfo(topic);
        List<QueueData> lists = topicRouteData.getQueueDatas();

        int readQ = 0;
        int writeQ = 0;
        int perm = 0;
        for (QueueData queueData:lists){
            readQ = Math.max(readQ, queueData.getReadQueueNums());
            writeQ = Math.max(writeQ, queueData.getWriteQueueNums());
            perm = Math.max(perm, queueData.getPerm());
        }
        topicConfig.setWriteQueueNums(writeQ);
        topicConfig.setReadQueueNums(readQ);
        topicConfig.setPerm(perm);

        if (null != topicConfig && null != topicConfig.getTopicName() && !topicConfig.getTopicName().isEmpty())
            return topicConfig;

        System.err.println("[sync topic] big error! find topic but no topic config !");
        return null;
    }

    private void rebuildTopicConfig(DefaultMQAdminExt defaultMQAdminExt, TopicConfig topicConfig) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        TopicRouteData topicRouteData = defaultMQAdminExt.examineTopicRouteInfo(topicConfig.getTopicName());
        List<BrokerData> brokerDatas = topicRouteData.getBrokerDatas();
        Set<String> localBroker = new HashSet<>();

        for (BrokerData brokerData:brokerDatas){
            for (Map.Entry<Long, String> entry:brokerData.getBrokerAddrs().entrySet()){
                localBroker.add(entry.getValue());
            }
        }

        for (String broker : brokerList) {
            if (localBroker.contains(broker))
                continue;
            defaultMQAdminExt.createAndUpdateTopicConfig(broker, topicConfig);
        }
    }
}
