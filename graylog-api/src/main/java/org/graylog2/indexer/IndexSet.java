/**
 * This file is part of Graylog.
 * <p>
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.indexer;

import org.graylog2.indexer.indexset.IndexSetConfig;
import org.graylog2.indexer.indices.TooManyAliasesException;

import javax.annotation.Nullable;

public interface IndexSet extends Comparable<IndexSet> {

    @Nullable
    String getActiveWriteIndex() throws TooManyAliasesException;

    String getIndexPrefix();

    IndexSetConfig getConfig();

    boolean isUp();
}
