package org.graylog2.indexer;

import java.util.Optional;
import java.util.Set;

public interface IndexSetRegistry extends Iterable<IndexSet> {
    Set<IndexSet> getAll();

    Optional<IndexSet> get(String indexSetId);
}