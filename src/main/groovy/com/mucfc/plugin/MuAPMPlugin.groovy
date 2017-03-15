package com.mucfc.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Created by abby on 2017/3/15.
 */

class MuAPMPlugin extends BasePlugin{
    @Override
    void apply(final Project project){
        super.apply(project)

        if(project.hasProperty('android')){
            project.android.registerTransform(new MuTransformer(project))
        }else{
            throw new GradleException("请先加载android的app或library插件");
        }
    }
}
