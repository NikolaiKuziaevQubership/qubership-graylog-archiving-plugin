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

import com.google.common.base.MoreObjects;

import java.util.Collections;
import java.util.List;

/**
 * {@code ElasticsearchException} is the superclass of those
 * exceptions that can be thrown during the normal interaction
 * with Elasticsearch.
 */
public class ElasticsearchException extends RuntimeException {
    private final List<String> errorDetails;

    public ElasticsearchException(String message) {
        super(message);
        this.errorDetails = Collections.emptyList();
    }

    public List<String> getErrorDetails() {
        return errorDetails;
    }

    @Override
    public String getMessage() {
        final StringBuilder sb = new StringBuilder(super.getMessage());

        if (!errorDetails.isEmpty()) {
            sb.append("\n\n");
            errorDetails.forEach(sb::append);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("message", getMessage())
                .add("errorDetails", getErrorDetails())
                .toString();
    }
}
