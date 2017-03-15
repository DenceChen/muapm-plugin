package com.mucfc.plugin

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Created by abby on 2017/3/15.
 */

@CompileStatic
class BasePlugin implements Plugin<Project> {
    Project project
    Logger logger

    @Override
    void apply(final Project project){
        this.project = project;
        this.logger = project.logger
    }


}
