package com.mucfc.plugin

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.api.transform.Context
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.mucfc.muapm.agent.InjectManager
import com.mucfc.muapm.agent.data.ClassData
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Created by abby on 2017/3/15.
 */

class MuTransformer extends Transform{
    Project project
    MuTransformer(Project project){

        this.project = project
    }

    String getName() {
        return "MuTransformer"
    }

    Set<QualifiedContent.ContentType> getInputTypes() {
        return EnumSet.of(QualifiedContent.DefaultContentType.CLASSES)
    }

    Set<QualifiedContent.Scope> getScopes() {
        return EnumSet.of(QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES)

    }

    @Override
    Set<QualifiedContent.Scope> getReferencedScopes() {
        return EnumSet.of(
                QualifiedContent.Scope.TESTED_CODE,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.PROVIDED_ONLY
        )
    }

    boolean isIncremental() {
        return false
    }

    /*private File getOutputDir(TransformInvocation invocation) {
        def provider = invocation.outputProvider
        def outDir = provider.getContentLocation(name, outputTypes, scopes, Format.DIRECTORY)
        outDir.deleteDir()
        outDir.mkdirs()

        outDir
    }*/

    private File getOutputDir(TransformOutputProvider outputProvider) {
        def provider = outputProvider
        def outDir = provider.getContentLocation(name, outputTypes, scopes, Format.DIRECTORY)
        outDir.deleteDir()
        outDir.mkdirs()

        outDir
    }


    /*void transform(TransformInvocation transformInvocation) throws IOException, TransformException, InterruptedException {
        def outDir = getOutputDir(transformInvocation)
        new Traverser() {
            @Override
            void processZipClass(ZipEntry entry, InputStream stream) {
                processClass(stream, outputForZip(entry))
            }

            @Override
            void processZipBytes(ZipEntry entry, InputStream stream) {
                outputForZip(entry) << stream
            }

            @Override
            void processDir(File baseDir, File file) {
                outputForFile(baseDir, file).mkdirs()
            }

            @Override
            void processFile(File baseDir, File file, InputStream stream) {
                def outputFile = outputForFile(baseDir, file)
                processClass(stream, outputFile)
            }

            File outputForZip(ZipEntry entry) {
                def outputFile = new File(outDir, entry.name)
                outputFile.parentFile.mkdirs()

                outputFile
            }

            File outputForFile(File baseDir, File file) {
                int baseDirLength = baseDir.absolutePath.length()
                def path = "${file.absolutePath[baseDirLength..-1]}"

                new File(outDir, path)
            }

        }.traverse(transformInvocation)
    }*/

    void transform(
            @NonNull Context context,
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs,
            @Nullable TransformOutputProvider outputProvider,
            boolean isIncremental) throws IOException, TransformException, InterruptedException {
        def outDir = getOutputDir(outputProvider)
        Logger logger = project.logger
        invokeTransform(inputs, referencedInputs, outDir, logger)
    }

    void invokeTransform(Collection<TransformInput> inputs,Collection<TransformInput> referencedInputs, File outDir, logger){
        Collection<TransformInput> allInputs = new ArrayList<>()
        allInputs.addAll(inputs)

        new Traverser() {
            @Override
            void processZipClass(ZipEntry entry, InputStream stream) {
                processClass(stream, outputForZip(entry))
            }

            @Override
            void processZipBytes(ZipEntry entry, InputStream stream) {
                outputForZip(entry) << stream
            }

            @Override
            void processDir(File baseDir, File file) {
                outputForFile(baseDir, file).mkdirs()
            }

            @Override
            void processFile(File baseDir, File file, InputStream stream) {
                def outputFile = outputForFile(baseDir, file)
                processClass(stream, outputFile)
            }

            File outputForZip(ZipEntry entry) {
                def outputFile = new File(outDir, entry.name)
                outputFile.parentFile.mkdirs()

                outputFile
            }

            File outputForFile(File baseDir, File file) {
                int baseDirLength = baseDir.absolutePath.length()
                def path = "${file.absolutePath[baseDirLength..-1]}"

                new File(outDir, path)
            }

        }.traverse(allInputs, logger)

    }





    void processClass(InputStream stream, File outputFile){
        byte[] classData = new byte[stream.available()]
        stream.read(classData)
        ClassData transformedData = InjectManager.transform(classData)
        outputFile.bytes = transformedData.classData
    }

}