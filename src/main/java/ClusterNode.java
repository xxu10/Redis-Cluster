
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.RedisClient;

public class ClusterNode {
    private String host;
    private int port;
    private int slotsBegin;
    private int slotsEnd;
    private String myId;
    private String masterId;
    private StatefulRedisConnection<String, String> connection;
    private RedisClient redisClient;

    public ClusterNode(String host, int port)
    {
        this.host = host;
        this.port = port;
        this.slotsBegin = 0;
        this.slotsEnd = 0;
        this.myId = null;
        this.masterId = null;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public void setMaster(String masterId)
    {
        this.masterId = masterId;
    }

    public String getMaster()
    {
        return masterId;
    }

    public void setMyId(String myId)
    {
        this.myId = myId;
    }

    public String getMyId()
    {
        return myId;
    }

    public void setSlotsBegin(int first)
    {
        this.slotsBegin = first;
    }

    public void setSlotsEnd(int last)
    {
        this.slotsEnd = last;
    }

    public int getSlotsBegin()
    {
        return slotsBegin;
    }

    public int getSlotsEnd()
    {
        return slotsEnd;
    }

    public void setConnection(StatefulRedisConnection<String, String> connection)
    {
        this.connection = connection;
    }

    public void setClient(RedisClient client)
    {
        this.redisClient = client;
    }

    public StatefulRedisConnection<String, String> getConnection()
    {
        return connection;
    }

    public RedisClient getClient()
    {
        return redisClient;
    }
}
