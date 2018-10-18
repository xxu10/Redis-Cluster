import io.lettuce.core.*;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ClusterManage {

    public static void main(String[] args) throws InterruptedException
    {
        List<ClusterNode> clusterNodeList = new ArrayList<>();
        List<ClusterNode> masterNodeList = new ArrayList<>();
        List<ClusterNode> slaveNodeList = new ArrayList<>();
        List<ClusterNode> isolatedNodeList = new ArrayList<>();
        List<ClusterNode> backupNodeList = new ArrayList<>();
        List<RedisURI> redisUriList = new ArrayList<>();
        Map<String,RedisClusterNode> curClusterAliveNodeMap = new HashMap<>();
        String[] endpoints = {"127.0.0.1:6379","127.0.0.1:6380","127.0.0.1:6381"
                ,"127.0.0.1:6382","127.0.0.1:6383","127.0.0.1:6384"};
        String[] backuppoints = {"127.0.0.1:6385","127.0.0.1:6386"};

        CreaterCluster.createCluster(clusterNodeList,masterNodeList,slaveNodeList,endpoints);

        for (ClusterNode node : clusterNodeList)
        {
            RedisURI redisUri = RedisURI.Builder.redis(node.getHost(), node.getPort()).build();
            redisUriList.add(redisUri);
        }

        ClusterNode leader = clusterNodeList.get(0);

        while(true){
            ClusterStatusDetect.clusterStatusDetect(leader,isolatedNodeList,backupNodeList,redisUriList,backuppoints,
                    curClusterAliveNodeMap);
        }

    }

}
