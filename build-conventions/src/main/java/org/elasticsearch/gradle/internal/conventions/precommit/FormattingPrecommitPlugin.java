/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.internal.conventions.precommit;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.List;

/**
 * This plugin configures formatting for Java source using Spotless
 * for Gradle. Since the act of formatting existing source can interfere
 * with developers' workflows, we don't automatically format all code
 * (yet). Instead, we maintain a list of projects that are excluded from
 * formatting, until we reach a point where we can comfortably format them
 * in one go without too much disruption.
 *
 * <p>Any new sub-projects must not be added to the exclusions list!
 *
 * <p>To perform a reformat, run:
 *
 * <pre>    ./gradlew spotlessApply</pre>
 *
 * <p>To check the current format, run:
 *
 * <pre>    ./gradlew spotlessJavaCheck</pre>
 *
 * <p>This is also carried out by the `precommit` task.
 *
 * <p>See also the <a href="https://github.com/diffplug/spotless/tree/master/plugin-gradle"
 * >Spotless project page</a>.
 */
public class FormattingPrecommitPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final boolean shouldFormatProject = PROJECT_PATHS_TO_EXCLUDE.contains(project.getPath()) == false
            || project.getProviders().systemProperty("es.format.everything").forUseAtConfigurationTime().isPresent();

        if (shouldFormatProject) {
            project.getPlugins().apply(PrecommitTaskPlugin.class);
            project.getPlugins().apply(SpotlessPlugin.class);

            project.getExtensions().getByType(SpotlessExtension.class).java(java -> {
                String importOrderPath = "build-conventions/elastic.importorder";
                String formatterConfigPath = "build-conventions/formatterConfig.xml";

                // When applied to e.g. `:build-tools`, we need to modify the path to our config files
                if (project.getRootProject().file(importOrderPath).exists() == false) {
                    importOrderPath = "../" + importOrderPath;
                    formatterConfigPath = "../" + formatterConfigPath;
                }

                java.target("src/**/*.java");

                // Use `@formatter:off` and `@formatter:on` to toggle formatting - ONLY IF STRICTLY NECESSARY
                java.toggleOffOn("@formatter:off", "@formatter:on");

                java.removeUnusedImports();

                // We enforce a standard order for imports
                java.importOrderFile(project.getRootProject().file(importOrderPath));

                // Most formatting is done through the Eclipse formatter
                java.eclipse().configFile(project.getRootProject().file(formatterConfigPath));

                // Ensure blank lines are actually empty. Since formatters are applied in
                // order, apply this one last, otherwise non-empty blank lines can creep
                // in.
                java.trimTrailingWhitespace();
            });

            project.getTasks().named("precommit").configure(precommitTask -> precommitTask.dependsOn("spotlessJavaCheck"));
        }
    }

    // Do not add new sub-projects here!
    private static final List<String> PROJECT_PATHS_TO_EXCLUDE = List.of(
        ":distribution:bwc:bugfix",
        ":distribution:bwc:maintenance",
        ":distribution:bwc:minor",
        ":distribution:bwc:staged"
    );
}
