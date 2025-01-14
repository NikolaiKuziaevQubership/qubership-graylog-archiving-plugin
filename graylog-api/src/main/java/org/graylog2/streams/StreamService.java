/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog2.streams;

import org.graylog2.plugin.database.PersistedService;
import org.graylog2.plugin.database.ValidationException;
import org.graylog2.plugin.streams.Stream;

import java.util.List;
import java.util.Map;

public interface StreamService extends PersistedService {
    Stream create(Map<String, Object> fields);

    String save(Stream stream) throws ValidationException;

    List<Stream> loadAll();
}
