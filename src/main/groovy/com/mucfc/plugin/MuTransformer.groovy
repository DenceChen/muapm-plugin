package com.mucfc.plugin

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.api.transform.Context
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.mucfc.muapm.agent.InjectManager
import com.mucfc.muapm.agent.data.ClassData
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        return true
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
        //invokeTransform(inputs, referencedInputs, outDir, logger)
        def count = 0;
        inputs.each { TransformInput input ->


            input.jarInputs.each { JarInput jarInput ->
                File dest = outputProvider.getContentLocation(jarInput.name + "_" + (count ++), jarInput.contentTypes, jarInput.scopes, Format.JAR)
                project.logger.error "JarInputs:${dest.name}\n${jarInput.file.absolutePath}"
                File intermediates = outputProvider.getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.DIRECTORY)
                FileUtils.copyFile(jarInput.file, dest)
                if (jarInput.file.absolutePath.contains("com.squareup.okhttp")) {
                    ZipFile zip = new ZipFile(jarInput.file)
                    zip.entries().findAll { zipEntry ->
                        !zipEntry.directory
                    }.each { zipEntry ->
                        InputStream entryStream = zip.getInputStream(zipEntry)
                        if (zipEntry.name.toLowerCase().endsWith('.class')) {
                            def outputFile = new File(intermediates, zipEntry.name)
                        //outputFile.parentFile.mkdirs()
                            outputFile.parentFile.mkdirs()
                        //outputFile << entryStream
                        //entryStream.close()
                            if(zipEntry.name.contains("com/squareup/okhttp/OkHttpClient.class" )) {
                                project.logger.error "entry name: ${zipEntry.name}"
                                processClass(entryStream, outputFile)
                            }else{
                                outputFile << entryStream
                            }
                        //makeJar(outputFile, dest)
                        //outputFile.delete()
                        } else {
                            def outputFile = new File(intermediates, zipEntry.name)
                            outputFile.parentFile.mkdirs()
                            outputFile << entryStream
                        //makeJar(outputFile, jarInput.file)

                        }
                        entryStream.close()
                        //makeJar(intermediates, dest)
                }
                zipMultiFile(intermediates, dest, false)
                deleteDir(intermediates)
                }
            }

            input.directoryInputs.each { DirectoryInput directoryInput ->
                project.logger.error "DirectoryInputs:${directoryInput.name}\n${directoryInput.file.absolutePath}"

                File dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                FileUtils.copyDirectory(directoryInput.file, dest)

            }

        }
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list()
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]))
                if (!success) {
                    return false
                }
            }
        }
        return dir.delete()
    }

    void makeJar(File outputFile, File jarFile) {
        try {
            if(jarFile.exists()) {
                jarFile.delete()
                jarFile.createNewFile()
            }

            InputStream jarIn = new FileInputStream(outputFile)
            ZipOutputStream jarOut = new ZipOutputStream(new FileOutputStream(jarFile))
            byte[] buf = new byte[1024]
            jarOut.putNextEntry(new ZipEntry(outputFile.getName()))
            int len
            while ((len = jarIn.read(buf)) != -1) {
                jarOut.write(buf, 0, len)
            }
            jarIn.close()
            jarOut.close()
            jarIn.close()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    void zipMultiFile(File file ,File zipFile, boolean dirFlag) {
        try {
            //File file = new File(filepath)// 要被压缩的文件夹
            //File zipFile = new File(zippath)
            if(zipFile.exists()) {
                zipFile.delete()
                zipFile.createNewFile()
            }

            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))
            if(file.isDirectory()){
                File[] files = file.listFiles()
                for(File fileSec:files){
                    if(dirFlag){
                        recursionZip(zipOut, fileSec, file.getName() + File.separator)
                    }else{
                        recursionZip(zipOut, fileSec, "")
                    }
                }
            }
            zipOut.close()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

     void recursionZip(ZipOutputStream zipOut, File file, String baseDir) throws Exception {
         if (file.isDirectory()) {
             File[] files = file.listFiles()
             for (File fileSec : files) {
                 recursionZip(zipOut, fileSec, baseDir + file.getName() + File.separator)
             }
         } else {
             byte[] buf = new byte[1024]
             InputStream input = new FileInputStream(file)
             zipOut.putNextEntry(new ZipEntry(baseDir + file.getName()))
             int len
             while ((len = input.read(buf)) != -1) {
                 zipOut.write(buf, 0, len)
             }
             input.close()
         }
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