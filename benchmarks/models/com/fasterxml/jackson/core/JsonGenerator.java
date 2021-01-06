package com.fasterxml.jackson.core;

public interface JsonGenerator
{
    public void writeStartObject();

    public void writeEndObject();

    public void writeFieldName(int i);

    public void writeString(int s);

    public void writeNumber(int i);

    public void writeBoolean(boolean b);

    public void writeStartArray();

    public void writeEndArray();

    public void flush();

    public void close();
}