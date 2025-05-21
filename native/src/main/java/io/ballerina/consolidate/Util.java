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
package io.ballerina.consolidate;

import io.ballerina.cli.cmd.CommandUtil;
import io.ballerina.cli.utils.FileUtils;
import io.ballerina.consolidate.model.Dependency;
import io.ballerina.projects.util.ProjectUtils;
import io.ballerina.toml.semantic.TomlType;
import io.ballerina.toml.semantic.ast.TomlArrayValueNode;
import io.ballerina.toml.semantic.ast.TomlKeyValueNode;
import io.ballerina.toml.semantic.ast.TomlStringValueNode;
import io.ballerina.toml.semantic.ast.TomlTableNode;
import io.ballerina.toml.semantic.ast.TomlValueNode;
import io.ballerina.toml.semantic.ast.TopLevelNode;
import io.ballerina.toml.validator.schema.ArraySchema;
import io.ballerina.toml.validator.schema.Schema;
import io.ballerina.toml.validator.schema.StringSchema;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for consolidate-packages tool.
 *
 * @since 0.1.0
 */
public class Util {

    // Constants related to the tool implementation
    static final String TOOL_NAME = "consolidate-packages";
    static final String BALLERINA_TOML = "Ballerina.toml";
    public static final String CONSOLIDATOR_BAL_FILE = "consolidator.bal";
    static final String NEW = "new";
    static final String ADD = "add";
    static final String REMOVE = "remove";
    static final String HYPHEN = "-";

    // Constants related to the consolidate-packages.properties file
    private static final String UNKNOWN = "unknown";
    public static final String TOOL_PROPERTIES_FILE = "consolidate-packages.properties";
    public static final String TOOL_VERSION = "tool.version";

    private Util() {}

    static Optional<Set<Dependency>> getServices(String services, String repo, String subCmd, PrintStream errStream)
            throws IOException {
        if (services == null || services.isEmpty()) {
            CommandUtil.printError(errStream, "no services provided", getUsage(subCmd), false);
            return Optional.empty();
        }
        Set<Dependency> dependencies = new LinkedHashSet<>();
        Set<String> serviceArray =  new LinkedHashSet<>(Arrays.asList(services.split(","))); ;
        Schema schema = Schema.from(FileUtils.readSchema(TOOL_NAME, Util.class.getClassLoader()));
        ArraySchema properties = (ArraySchema) schema.properties().get("services");
        Optional<String> optionalPattern = ((StringSchema) properties.items()).pattern();
        if (optionalPattern.isEmpty()) {
            throw new IllegalStateException("unable to find the pattern for services in the tool schema");
        }
        Pattern pattern = Pattern.compile(optionalPattern.get());
        boolean isValid = true;
        for (String service : serviceArray) {
            Matcher matcher = pattern.matcher(service);
            if (!matcher.matches()) {
                String msg = properties.items().message().get("pattern");
                CommandUtil.printError(errStream, "'" + service + "': " + msg, null, false);
                isValid = false;
                continue;
            }
            
            String org = matcher.group(1);
            String name = matcher.group(2);
            if (repo == null) {
                dependencies.add(new Dependency(org, name));
            } else {
                String version = matcher.group(3);
                if (version == null) {
                    CommandUtil.printError(errStream, "package creation failed. Service " + service
                                    + " must contain the version when the repository is provided.",
                            null, false);
                    isValid = false;
                }
                dependencies.add(new Dependency(org, name, version, repo));
            }
        }
        return isValid ? Optional.of(dependencies) : Optional.empty();
    }

     static Set<Dependency> getServices(TomlTableNode tomlTableNode) {
        Set<Dependency> dependencies = new HashSet<>();
        TopLevelNode servicesNode = tomlTableNode.entries().get("services");
         if (servicesNode.kind() == null || servicesNode.kind() != TomlType.KEY_VALUE) {
             return dependencies;
         }
         TomlKeyValueNode keyValueNode = (TomlKeyValueNode) servicesNode;
         TomlValueNode valueNode = keyValueNode.value();
         if (valueNode.kind() != TomlType.ARRAY) {
             return dependencies;
         }
         TomlArrayValueNode arrayValueNode = (TomlArrayValueNode) valueNode;

         for (TomlValueNode value : arrayValueNode.elements()) {
             if (value.kind() == TomlType.STRING) {
                 String[] values = ((TomlStringValueNode) value).getValue().split("/");
                 dependencies.add(new Dependency(values[0], values[1]));
             }
         }
        return dependencies;
    }

     static void replaceServicesArrayInToml(Set<Dependency> allServices, String dependencyEntries, Path balTomlPath,
                                            Set<Dependency> rmServices)
            throws IOException {
        String content = Files.readString(balTomlPath);
        Pattern pattern = Pattern.compile("options\\.services\\s*=\\s*\\[(?:\\s*\"[^\"]+\"\\s*,?\\s*)+]",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return;
        }
        String existingStr = matcher.group();
        StringBuilder replacementStr = new StringBuilder("options.services = [");
        for (Iterator<Dependency> iterator = allServices.iterator(); iterator.hasNext(); ) {
            Dependency service = iterator.next();
            replacementStr.append("\"").append(service).append("\"");
            if (iterator.hasNext()) {
                replacementStr.append(",");
            }
        }
        replacementStr.append("]");
        String modifiedContent = content.replace(existingStr, replacementStr) + dependencyEntries;
        if (rmServices != null && !rmServices.isEmpty()) {
            for (Dependency service : rmServices) {
                String dependencyRegex = "\\[\\[dependency]]\\s*" +
                        "org\\s*=\\s*\"" + service.org() + "\"\\s*" +
                        "name\\s*=\\s*\"" + service.name() + "\"\\s*" +
                        "version\\s*=\\s*\"(.*?)\"\\s*" +
                        "repository\\s*=\\s*\"(.*?)\"";
                Pattern dependencyPattern = Pattern.compile(dependencyRegex);
                Matcher dependencyMatcher = dependencyPattern.matcher(modifiedContent);
                if (!dependencyMatcher.find()) {
                    continue;
                }
                String existingDep = dependencyMatcher.group();
                modifiedContent = modifiedContent.replace(existingDep, "");
            }
        }
        Files.writeString(balTomlPath, modifiedContent, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static String getHelpText(String commandName) {
        try (InputStream inputStream = Util.class.getClassLoader()
                .getResourceAsStream("ballerina-" + commandName + ".help");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "help text not found";
        }
    }

    static String validatePackageName(String packageName, PrintStream outStream) {
        if (!ProjectUtils.validatePackageName(packageName)) {
            packageName = ProjectUtils.guessPkgName(packageName, "default");
            outStream.println("Package name is derived as '" + packageName
                    + "'. Edit the Ballerina.toml to change it.");
            outStream.println();
        }
        return packageName;
    }

    static String getUsage(String subCmd) {
        return "Run 'bal consolidate-package " + subCmd + "--help' for usage.";
    }

    /**
     * Get the version of the tool.
     *
     * @return version of the tool
     */
    public static String getToolVersion() {
        String versionPrefix = "consolidate-packages version ";
        try (InputStream inputStream = Util.class.getClassLoader().getResourceAsStream(TOOL_PROPERTIES_FILE)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return versionPrefix + properties.getProperty(TOOL_VERSION);
        } catch (Throwable ignore) {
        }
        return versionPrefix + UNKNOWN;
    }

    static String getDependencyEntries(Set<Dependency> services) {
        StringBuilder repoEntries = new StringBuilder();
        for (Dependency dependency : services) {
            repoEntries.append("\n[[dependency]]\n")
                    .append("org = \"").append(dependency.org()).append("\"\n")
                    .append("name = \"").append(dependency.name()).append("\"\n")
                    .append("version = \"").append(dependency.version().orElseThrow()).append("\"\n")
                    .append("repository = \"").append(dependency.repository().orElseThrow()).append("\"\n");
        }
        return repoEntries.toString();
    }
}
