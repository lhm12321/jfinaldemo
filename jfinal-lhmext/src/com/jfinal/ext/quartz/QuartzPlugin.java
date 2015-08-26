/**
 * Copyright (c) 2011-2013, kidzhou 周磊 (zhouleib1412@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jfinal.ext.quartz;

import com.jfinal.ext.kit.Reflect;
import com.jfinal.log.Logger;
import com.jfinal.plugin.IPlugin;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class QuartzPlugin implements IPlugin {
    private static final String JOB = "job";
    private final Logger logger = Logger.getLogger(getClass());
    private Map<Job,String> jobs = new HashMap<Job, String>();
    private SchedulerFactory sf;
    private Scheduler scheduler;
    private String jobConfig = "job.properties";
    private String confConfig = "";
    private Properties jobProp;

    public QuartzPlugin(String jobConfig,String confConfig) {
        this.jobConfig = jobConfig;
        this.confConfig = confConfig;
    }

    public QuartzPlugin(String jobConfig) {
        this.jobConfig = jobConfig;
    }

    public QuartzPlugin() {
    }

    public QuartzPlugin add(String jobCronExp, Job job) {
        jobs.put(job,jobCronExp);
        return this;
    }
    @Override
    public boolean start() {
        loadJobsFromProperties();
        startJobs();
        return true;
    }

    private void startJobs() {
        try {
        	if(!confConfig.equals("")){
        		sf = new StdSchedulerFactory(confConfig);
        	}else{
        		sf = new StdSchedulerFactory();
        	}
            scheduler = sf.getScheduler();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        Set<Map.Entry<Job,String>> set = jobs.entrySet();
        for (Map.Entry<Job,String> entry : set) {
            Job job = entry.getKey();
            String jobClassName = job.getClass().getName();
            String jobCronExp = entry.getValue();
            JobDetail jobDetail;
            CronTrigger trigger;
            //JobDetail and CornTrigger are classes in 1.x version,but are interfaces in 2.X version.
            jobDetail = Reflect.on("org.quartz.JobBuilder").call("newJob", job.getClass()).call("withIdentity", jobClassName, jobClassName)
                    .call("build").get();
            Object temp = Reflect.on("org.quartz.TriggerBuilder").call("newTrigger").get();
            temp = Reflect.on(temp).call("withIdentity", jobClassName, jobClassName).get();
            temp = Reflect.on(temp).call("withSchedule",
                    Reflect.on("org.quartz.CronScheduleBuilder").call("cronSchedule", jobCronExp).get())
                    .get();
            trigger = Reflect.on(temp).call("build").get();
            Date ft = Reflect.on(scheduler).call("scheduleJob", jobDetail, trigger).get();
            logger.debug(Reflect.on(jobDetail).call("getKey") + " has been scheduled to run at: " + ft + " " +
                    "and repeat based on expression: " + Reflect.on(trigger).call("getCronExpression"));
        }
        try {
            scheduler.start();
        } catch (SchedulerException e) {
        	e.printStackTrace();
        }
    }

    private void loadJobsFromProperties() {
        loadProperties();
        Enumeration<Object> enums = jobProp.keys();
        while (enums.hasMoreElements()) {
            String key = enums.nextElement() + "";
            if (!key.endsWith(JOB) || !isEnableJob(enable(key))) {
                continue;
            }
            String jobClassName = jobProp.get(key) + "";
            String jobCronExp = jobProp.getProperty(cronKey(key)) + "";
            Class<Job> job = Reflect.on(jobClassName).get();
            try {
                jobs.put(job.newInstance(),jobCronExp);
            } catch (Exception e) {
            	e.printStackTrace();
            }
        }
    }

    private String enable(String key) {
        return key.substring(0, key.lastIndexOf(JOB)) + "enable";
    }

    private String cronKey(String key) {
        return key.substring(0, key.lastIndexOf(JOB)) + "cron";
    }

    private boolean isEnableJob(String enableKey) {
        Object enable = jobProp.get(enableKey);
        if (enable != null && "false".equalsIgnoreCase((enable + "").trim())) {
            return false;
        }
        return true;
    }

    private void loadProperties() {
        jobProp = new Properties();
        InputStream is = QuartzPlugin.class.getClassLoader().getResourceAsStream(jobConfig);
        try {
            jobProp.load(is);
        } catch (IOException e) {
        	e.printStackTrace();
        }
        logger.debug("------------load Job Propteries---------------");
        logger.debug(jobProp.toString());
        logger.debug("------------------------------------------");
    }

    @Override
    public boolean stop() {
        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
        	e.printStackTrace();
        }
        return true;
    }

    public QuartzPlugin confConfig(String confConfig) {
        this.confConfig = confConfig;
        return this;
    }

    public QuartzPlugin jobConfig(String jobConfig) {
        this.jobConfig = jobConfig;
        return this;
    }
}