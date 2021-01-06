package org.apache.hadoop.conf;

public class Configuration
{
    public int get(int val)
    {
        if (nd$boolean())
        {
            return val;
        }

        return 0;
    }

    public boolean getBoolean(int val, boolean defaultVal)
    {
        if (nd$boolean())
            return true;

        return defaultVal;
    }

    private boolean nd$boolean()
    {
        return false;
    }
}