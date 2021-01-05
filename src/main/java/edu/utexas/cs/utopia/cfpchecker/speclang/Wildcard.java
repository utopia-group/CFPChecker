package edu.utexas.cs.utopia.cfpchecker.speclang;

import org.apache.commons.lang.Validate;

/**
 * Created by kferles on 6/27/18.
 */
final public class Wildcard
{
    public static Wildcard DONT_CARE_VALUE = new Wildcard();

    private int id = -1;

    private boolean distinct = false;

    private Wildcard()
    {

    }


    public Wildcard(int id)
    {
        Validate.isTrue(id >= 0, "Id must be positive!");

        this.id = id;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Wildcard wildcard = (Wildcard) o;

        return id == wildcard.id;
    }

    @Override
    public int hashCode()
    {
        return id;
    }

    @Override
    public String toString()
    {
        return id == -1 ? "_" : ("$" + id);
    }
}
