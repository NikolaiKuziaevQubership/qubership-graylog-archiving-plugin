package org.graylog2.indexer.indices;

import org.graylog2.indexer.ElasticsearchException;

import java.util.Set;

public class TooManyAliasesException extends ElasticsearchException {
    private final Set<String> indices;

    public TooManyAliasesException(final Set<String> indices) {
        super("More than one index in deflector alias: " + indices.toString());
        this.indices = indices;
    }

    public Set<String> getIndices() {
        return indices;
    }
}
