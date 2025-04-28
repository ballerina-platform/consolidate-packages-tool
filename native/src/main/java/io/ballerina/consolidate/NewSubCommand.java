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

import io.ballerina.cli.BLauncherCmd;
import io.ballerina.cli.cmd.CommandUtil;
import io.ballerina.consolidate.model.Dependency;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import static io.ballerina.consolidate.Util.HYPHEN;
import static io.ballerina.consolidate.Util.NEW;
import static io.ballerina.consolidate.Util.TOOL_NAME;
import static io.ballerina.consolidate.Util.getDependencyEntries;

/**
 * This class represents the "consolidate-packages new" sub command.
 *
 * @since 0.1.0
 */
@CommandLine.Command(name = NEW, description = "Creates a new Ballerina package to consolidate the given services")
public class NewSubCommand implements BLauncherCmd {
    private final PrintStream outStream;
    private final PrintStream errStream;
    boolean exit;

    @CommandLine.Parameters (arity = "0..1")
    private String servicesStr;

    @CommandLine.Option(names = {"--package-path"})
    private String packagePath;

    @CommandLine.Option(names = {"--repository"})
    private String repository;

    @CommandLine.Option(names = {"--help", "-h"})
    private boolean help;

    public NewSubCommand() {
        this.outStream = System.out;
        this.errStream = System.err;
        this.exit = true;
        CommandUtil.initJarFs();
    }

    public NewSubCommand(PrintStream printStream) {
        this.outStream = printStream;
        this.errStream = printStream;
        this.help = true;
    }

    public NewSubCommand(PrintStream printStream, String packagePath, String servicesStr, boolean exit) {
        this.outStream = printStream;
        this.errStream = printStream;
        this.packagePath = packagePath;
        this.servicesStr = servicesStr;
        this.exit = exit;
        CommandUtil.initJarFs();
    }

    @Override
    public void execute() {
        if (help || servicesStr == null) {
            outStream.println(Util.getHelpText(getName()));
            return;
        }
        Set<Dependency> services;
        try {
            Optional<Set<Dependency>> optionalList = Util.getServices(servicesStr, repository, NEW, errStream);
            if (optionalList.isEmpty()) {
                CommandUtil.exitError(this.exit);
                return;
            }
            services = optionalList.get();
        } catch (Exception e) {
            CommandUtil.printError(this.errStream, "Failed to extract the services.", null, false);
            CommandUtil.exitError(exit);
            return;
        }

        try {
            Path pkgPath = Paths.get(packagePath);
            createProject(pkgPath, services);
        } catch (IOException | URISyntaxException e) {
            CommandUtil.printError(this.errStream, "Package creation failed, reason: " + e.getMessage(),
                    null, false);
            CommandUtil.exitError(exit);
        }
    }

    private void createProject(Path packagePath, Set<Dependency> services) throws IOException, URISyntaxException {
        outStream.println("Generating the consolidator package for");
        for (Dependency service : services) {
            outStream.println("\t" + service);
        }
        Files.createDirectories(packagePath);
        String packageName = Util.validatePackageName(packagePath.getFileName().toString(), outStream);
        CommandUtil.initPackageByTemplate(packagePath, packageName, "default", true);

        StringJoiner options = new StringJoiner(",");
        for (Dependency service : services) {
            options.add("\"" + service + "\"");
        }
        String toolEntry = "\n[[tool." + TOOL_NAME + "]]\n" +
                "id = " + "\"consolidatePackages1\"\n" +
                "options.services = [" +
                options + "]\n";
        String dependencyEntries = repository == null ? "" : getDependencyEntries(services);

        Files.writeString(packagePath.resolve(Util.BALLERINA_TOML), toolEntry + dependencyEntries,
                StandardOpenOption.APPEND);

        // Generate the main.bal
        String consolidatorMainBal = """
                import ballerina/log;
                
                public function main() {
                    log:printInfo("Started all services");
                }
                """;
        Files.writeString(packagePath.resolve("main.bal"), consolidatorMainBal);

        outStream.println("\nSuccessfully created the consolidator package at '" + this.packagePath + "'.\n");
        outStream.println("What's next?\n\t Execute 'bal build " + this.packagePath + "' to generate the executable.");
    }

    @Override
    public String getName() {
        return TOOL_NAME + HYPHEN + NEW;
    }

    @Override
    public void printLongDesc(StringBuilder stringBuilder) {

    }

    @Override
    public void printUsage(StringBuilder stringBuilder) {

    }

    @Override
    public void setParentCmdParser(CommandLine commandLine) {

    }
}
