import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import io.lettuce.core.cluster.models.partitions.ClusterPartitionParser;
import io.lettuce.core.cluster.models.partitions.Partitions;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClusterStatusDetect {

    public static void clusterStatusDetect(ClusterNode node, List<ClusterNode> isolatedNodeList,
                                            List<ClusterNode> backupNodeList,List<RedisURI> redisUriList,
                                            String[] backuppoints, Map<String,RedisClusterNode> curClusterAliveNodeMap)
            throws InterruptedException
    {
            RedisURI redisUri = RedisURI.Builder.redis(node.getHost(), node.getPort()).build();
            RedisClient redisClient = RedisClient.create(redisUri);
            StatefulRedisConnection<String, String> connection = redisClient.connect();
            Partitions partitions = ClusterPartitionParser.parse((connection.sync().clusterNodes()));
            if(partitions.size() == 1)
            {
                isolatedNodeList.add(node);

            }
            else
            {
                for (RedisClusterNode partition : partitions)
                {
                    if(partition.getFlags().contains(RedisClusterNode.NodeFlag.FAIL)
                            || partition.getFlags().contains(RedisClusterNode.NodeFlag.EVENTUAL_FAIL)
                            || partition.getFlags().contains(RedisClusterNode.NodeFlag.NOADDR)) {
                        System.out.println("There is a node offline ...");
                        System.out.println("Wait for 5s ...");
                        Thread.sleep(5000);
                        if(partition.getFlags().contains(RedisClusterNode.NodeFlag.FAIL)
                                || partition.getFlags().contains(RedisClusterNode.NodeFlag.EVENTUAL_FAIL)
                                || partition.getFlags().contains(RedisClusterNode.NodeFlag.NOADDR))
                        {
                            System.out.println("This node is still offline, use new node instead ...");
                            connection.sync().clusterForget(partition.getNodeId());
                            List<RedisURI> newList = new ArrayList<>(redisUriList);
                            newList.remove(partition.getUri());
                            curClusterAliveNodeMap.remove(partition.getNodeId());
                            AddNewNode(backuppoints,newList,backupNodeList,curClusterAliveNodeMap);
                            continue;
                        }
                        else {
                            RedisClient tempClient = RedisClient.create(partition.getUri());
                            StatefulRedisConnection<String, String> balanceCon = tempClient.connect();
                            balanceCon.sync().clusterFailover(false);
                            balanceCon.close();
                            tempClient.shutdown();
                        }
                    }
                    curClusterAliveNodeMap.put(partition.getNodeId(),partition);
                }

            }
            connection.close();
            redisClient.shutdown();
    }

    public static void AddNewNode(String[] backuppoints, List<RedisURI> redisUriList, List<ClusterNode> backupNodeList,
                                  Map<String,RedisClusterNode> curClusterAliveNodeMap)
    {
        String[] endpoints = backuppoints;
        RedisClusterClient redisClusterClient = RedisClusterClient.create(redisUriList);
        StatefulRedisClusterConnection<String, String> clusterCon = redisClusterClient.connect();
        RedisClusterCommands<String, String> commands = clusterCon.sync();

        List<String> masterNode = new ArrayList<>();
        List<String> withSlaveNode = new ArrayList<>();
        Partitions partitions = ClusterPartitionParser.parse(commands.clusterNodes());
        for (RedisClusterNode partition : partitions) {
            if (partition.getFlags().contains(RedisClusterNode.NodeFlag.MASTER)){
                masterNode.add(partition.getNodeId());
                //System.out.println(partition.getNodeId());
            }

            else if (partition.getFlags().contains(RedisClusterNode.NodeFlag.SLAVE)) {
                withSlaveNode.add(partition.getSlaveOf());
                //System.out.println("withslave"+partition.getSlaveOf());
            }
        }

        masterNode.removeAll(withSlaveNode);

        for (String endpoint : endpoints)
        {
            String[] ipAndPort = endpoint.split(":");
            ClusterNode node = new ClusterNode(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
            backupNodeList.add(node);
            try
            {
                commands.clusterMeet(ipAndPort[0],Integer.parseInt(ipAndPort[1]));
            }catch (RedisException e)
            {
                System.out.println("Meet failed-->" + ipAndPort[0] + ":" + ipAndPort[1]);
            }

        }
        int index = 0;
        for (String nodeID : masterNode){
            try
            {
                RedisURI redisUri = RedisURI.Builder.redis(backupNodeList.get(index).getHost(),
                        backupNodeList.get(index).getPort()).build();
                RedisClient redisClient = RedisClient.create(redisUri);
                StatefulRedisConnection<String, String> slaveCon = redisClient.connect();
                slaveCon.sync().clusterReplicate(nodeID);
                slaveCon.sync().clusterFailover(false);
                backupNodeList.get(index).setConnection(slaveCon);
                index++;
                slaveCon.close();
                redisClient.shutdown();
            }
            catch (RedisCommandTimeoutException | RedisConnectionException e)
            {
                System.out.println("replicate failed-->" + nodeID);
            }
        }
        clusterCon.close();
        redisClusterClient.shutdown();
    }

}
