/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.acacia.dataflow;

import com.acacia.dataflow.common.*;
import com.acacia.scaffolding.AbstractTransform;

import com.acacia.scaffolding.AbstractTransformComposer;
import com.google.api.client.util.Lists;
import com.google.cloud.dataflow.sdk.Pipeline;
import com.google.cloud.dataflow.sdk.PipelineResult;
import com.google.cloud.dataflow.sdk.io.PubsubIO;
import com.google.cloud.dataflow.sdk.io.Read;
import com.google.cloud.dataflow.sdk.options.PipelineOptionsFactory;
import com.google.cloud.dataflow.sdk.runners.DataflowPipeline;
import com.google.cloud.dataflow.sdk.runners.DataflowPipelineRunner;
import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.transforms.ParDo;
import org.apache.avro.generic.GenericData;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.*;

/**
 * <p>This pipeline example reads lines of text from a PubSub topic, splits each line
 * into individual words, capitalizes those words, and writes the output to
 * a BigQuery table.
 * <p>
 * <p>By default, the example will inject the data from the the Pub/Sub {@literal --pubsubTopic}.
 * It will make it available for the streaming pipeline to process.
 */
public class Main {


    static URLClassLoader classloader;

    /**
     * A DoFn that uppercases a word.
     */
    static class Append extends DoFn<String, String> {
        @Override
        public void processElement(ProcessContext c) {
            c.output(c.element() + "w00t");
        }
    }


    /**
     * Sets up and starts streaming pipeline.
     *
     * NOTE: needs to take some kind of exectution graph as an argument? paths to oooo
     *
     * @throws IOException if there is a problem setting up resources
     */
    public static void main(String[] args) throws IOException {


        ComposerManagerOptions options = PipelineOptionsFactory.fromArgs(args)
                .withValidation()
                .as(ComposerManagerOptions.class);
        options.setStreaming(true);
        // In order to cancel the pipelines automatically,
        // {@literal DataflowPipelineRunner} is forced to be used.
        options.setRunner(DataflowPipelineRunner.class);
        options.setMaxNumWorkers(1);
        options.setNumWorkers(1);
        options.setWorkerMachineType("n1-standard-1");

        List<String> stagingFiles = Lists.newArrayList(Arrays.asList(options.getExternalFiles().split(",")));

        //shouldn't be necessary -- google uploads classpath automatically
        //options.setFilesToStage(stageFiles(stagingFiles));

        List<String> outputTopics = Arrays.asList(options.getOutputTopics().split(","));
        List<String> executionPipelineClasses = Arrays.asList(options.getExecutionPipelineClasses().split(","));


        //List<Class<?>> transforms = getAllClasses(executionPipelineClasses, stagingFiles);

        List<Class<?>> transforms = new ArrayList<>();

        DataflowUtils dataflowUtils = new DataflowUtils(options);
        dataflowUtils.setup();


        //NOTE -- ALWAYS BUNDLE DEPENDENCIES IN CLASS JARS

        String errorPipeline = "projects/" + options.getProject()
                + "/topics/" + options.getJobName() + "/error";

        ServiceLoader<AbstractTransformComposer> loader = null;
        loader = ServiceLoader.load(AbstractTransformComposer.class, ClassLoader.getSystemClassLoader());

        List<AbstractTransformComposer> transformComposers = new ArrayList<>();


        //jythontest

        JythonFactory jf = JythonFactory.getInstance();
        AbstractTransform tf = (AbstractTransform) jf.getJythonObject(
                "com.acacia.scaffolding.AbstractTransform","~/proj/pypipes/acacia-common/__init__.py");

       Iterator<AbstractTransformComposer> transformsf = loader.iterator();

        while (transformsf.hasNext()) {

            AbstractTransformComposer c = transformsf.next();

            transformComposers.add(c);

            for (AbstractTransform t : c.getOrderedTransforms()) {


                System.out.println("Examining: " + t.getClass().getCanonicalName());
                if (executionPipelineClasses.contains(t.getClass().getCanonicalName())) {
                    System.out.println("Loading: " + t.getClass().getCanonicalName());
                }

            }

        }

        //NOTE: to get this working, we need to include computation jars in the classpath. ew.

        Pipeline pipeline = DataflowPipeline.create(options);

        //need to support more than ParDo.of in scaffolding

        pipeline.apply(PubsubIO.Read.topic(options.getPubsubTopic()))

                .apply(new MultiTransform())
                //.apply(ParDo.of(new Append()));
                .apply(MultiWrite.topics(outputTopics));


        PipelineResult result = pipeline.run();

    }



}