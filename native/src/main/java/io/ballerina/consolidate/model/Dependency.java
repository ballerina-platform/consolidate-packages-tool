/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.consolidate.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a dependency (service/task).
 *
 * @since 1.1.0
 */
public final class Dependency {
    private final String org;
    private final String name;
    private String version;
    private String repository;

    public Dependency(String org, String name, String version, String repository) {
        this.org = org;
        this.name = name;
        this.version = version;
        this.repository = repository;
    }

    public Dependency(String org, String name) {
        this.org = org;
        this.name = name;
    }

    public String org() {
        return org;
    }

    public String name() {
        return name;
    }

    public Optional<String> version() {
        return Optional.ofNullable(version);
    }

    public Optional<String> repository() {
        return Optional.ofNullable(repository);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (Dependency) obj;
        return Objects.equals(this.org, that.org) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(org, name, version);
    }

    @Override
    public String toString() {
        return org + "/" + name;
    }

}
