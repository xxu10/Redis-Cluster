
import java.util.List;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;


public class CreaterCluster {

    public static void createCluster( List<ClusterNode> clusterNodeList,List<ClusterNode> masterNodeList,
                                      List<ClusterNode> slaveNodeList, String[] endpoints) throws InterruptedException
    {

        int index = 0;
        for (String endpoint : endpoints)
        {
            String[] ipAndPort = endpoint.split(":");
            ClusterNode node = new ClusterNode(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
            clusterNodeList.add(node);

            if (index < 3)
            {
                masterNodeList.add(node);
            }
            else
            {
                slaveNodeList.add(node);
            }
            index++;
        }

        for (ClusterNode node : clusterNodeList)
        {
            RedisURI redisUri = RedisURI.Builder.redis(node.getHost(), node.getPort()).build();
            RedisClient redisClient = RedisClient.create(redisUri);
            try
            {
                StatefulRedisConnection<String, String> connection = redisClient.connect();
                node.setConnection(connection);
                node.setMyId(connection.sync().clusterMyId());
                node.setClient(redisClient);
            } catch (RedisException e)
            {
                System.out.println("connection failed-->" + node.getHost() + ":" + node.getPort());
            }
        }

        ClusterNode firstNode = null;
        for (ClusterNode node : clusterNodeList)
        {
            if (firstNode == null)
            {
                firstNode = node;
            }
            else
            {
                try
                {
                    node.getConnection().sync().clusterMeet(firstNode.getHost(), firstNode.getPort());
                }
                catch (RedisCommandTimeoutException | RedisConnectionException e)
                {
                    System.out.println("meet failed-->" + node.getHost() + ":" + node.getPort());
                }
            }
        }

        int[] slots = {0,5460,5461,10921,10922,16383};
        index = 0;
        for (ClusterNode node : masterNodeList)
        {
            node.setSlotsBegin(slots[index]);
            index++;
            node.setSlotsEnd(slots[index]);
            index++;
        }

        System.out.println("Start to set slots...");
        for (ClusterNode node : masterNodeList)
        {
            try
            {
                node.getConnection().sync().clusterAddSlots(createSlots(node.getSlotsBegin(), node.getSlotsEnd()));
            }
            catch (RedisCommandTimeoutException | RedisConnectionException e)
            {
                System.out.println("add slots failed-->" + node.getHost() + ":" + node.getPort());
            }
        }

        Thread.sleep(5000);

        index = 0;
        for (ClusterNode node : slaveNodeList)
        {
            try
            {
                node.getConnection().sync().clusterReplicate(masterNodeList.get(index).getMyId());
                index++;
            }
            catch (RedisCommandTimeoutException | RedisConnectionException e)
            {
                System.out.println("replicate failed-->" + node.getHost() + ":" + node.getPort());
            }
        }
        System.out.println("Successfully created Redis Cluster ...");
        for (ClusterNode node : clusterNodeList)
        {
            node.getConnection().close();
            node.getClient().shutdown();
        }
    }

    public static int[] createSlots(int from, int to)
    {
        int[] result = new int[to - from + 1];
        int counter = 0;
        for (int i = from; i <= to; i++)
        {
            result[counter++] = i;
        }
        return result;
    }



}

