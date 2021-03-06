/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gobblin.broker;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import gobblin.broker.gobblin_scopes.GobblinScopeInstance;
import gobblin.broker.gobblin_scopes.GobblinScopeTypes;
import gobblin.broker.gobblin_scopes.JobScopeInstance;
import gobblin.broker.gobblin_scopes.TaskScopeInstance;
import gobblin.broker.iface.NoSuchScopeException;


public class DefaultGobblinBrokerTest {

  private static final Joiner JOINER = Joiner.on(".");

  @Test
  public void testSharedObjects() throws Exception {
    // Correct creation behavior
    Config config = ConfigFactory.empty();

    SharedResourcesBrokerImpl<GobblinScopeTypes> topBroker = SharedResourcesBrokerFactory.createDefaultTopLevelBroker(config,
        GobblinScopeTypes.GLOBAL.defaultScopeInstance());
    SharedResourcesBrokerImpl<GobblinScopeTypes> jobBroker =
        topBroker.newSubscopedBuilder(new JobScopeInstance("myJob", "job123")).build();
    SharedResourcesBrokerImpl<GobblinScopeTypes>
        containerBroker = topBroker.newSubscopedBuilder(GobblinScopeTypes.CONTAINER.defaultScopeInstance()).build();
    SharedResourcesBrokerImpl<GobblinScopeTypes> taskBroker = jobBroker.newSubscopedBuilder(new TaskScopeInstance("taskabc"))
        .withAdditionalParentBroker(containerBroker).build();
    SharedResourcesBrokerImpl<GobblinScopeTypes> taskBroker2 = jobBroker.newSubscopedBuilder(new TaskScopeInstance("taskxyz"))
        .withAdditionalParentBroker(containerBroker).build();

    // create a shared resource
    TestFactory.SharedResource resource =
        taskBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.JOB);

    Assert.assertEquals(resource.getKey(), "myKey");

    // using same broker with same scope and key returns same object
    Assert.assertEquals(taskBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.JOB),
        resource);
    // using different broker with same scope and key returns same object
    Assert.assertEquals(taskBroker2.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.JOB),
        resource);
    Assert.assertEquals(jobBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.JOB),
        resource);

    // Using different key returns a different object
    Assert.assertNotEquals(taskBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("otherKey"), GobblinScopeTypes.JOB),
        resource);
    // Using different scope returns different object
    Assert.assertNotEquals(taskBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.TASK),
        resource);
    // Requesting unscoped resource returns different object
    Assert.assertNotEquals(taskBroker.getSharedResource(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey")),
        resource);
  }

  @Test
  public void testConfigurationInjection() throws Exception {

    String key = "myKey";

    Config config = ConfigFactory.parseMap(ImmutableMap.of(
        JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, TestFactory.NAME, "key1"), "value1",
        JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, TestFactory.NAME, "key2"), "value2",
        JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, TestFactory.NAME, GobblinScopeTypes.CONTAINER.name(), "key2"), "value2scope",
        JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, TestFactory.NAME, key, "key2"), "value2key",
        JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, TestFactory.NAME, GobblinScopeTypes.CONTAINER.name(), key, "key2"), "value2scopekey"
    ));

    SharedResourcesBrokerImpl<GobblinScopeTypes> topBroker = SharedResourcesBrokerFactory.createDefaultTopLevelBroker(config,
        GobblinScopeTypes.GLOBAL.defaultScopeInstance());
    SharedResourcesBrokerImpl<GobblinScopeTypes>
        containerBroker = topBroker.newSubscopedBuilder(GobblinScopeTypes.CONTAINER.defaultScopeInstance()).build();

    // create a shared resource
    TestFactory.SharedResource resource =
        containerBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.CONTAINER);

    Assert.assertEquals(resource.getConfig().getString("key1"), "value1");
    Assert.assertEquals(resource.getConfig().getString("key2"), "value2scopekey");
  }

  @Test
  public void testScoping() throws Exception {
    // Correct creation behavior
    Config config = ConfigFactory.empty();

    SharedResourcesBrokerImpl<GobblinScopeTypes> topBroker = SharedResourcesBrokerFactory.createDefaultTopLevelBroker(config,
        GobblinScopeTypes.GLOBAL.defaultScopeInstance());
    SharedResourcesBrokerImpl<GobblinScopeTypes> jobBroker =
        topBroker.newSubscopedBuilder(new JobScopeInstance("myJob", "job123")).build();

    Assert.assertEquals(jobBroker.getScope(GobblinScopeTypes.INSTANCE).getType(), GobblinScopeTypes.INSTANCE);
    Assert.assertEquals(jobBroker.getScope(GobblinScopeTypes.INSTANCE).getClass(), GobblinScopeInstance.class);
    Assert.assertEquals(jobBroker.getScope(GobblinScopeTypes.INSTANCE), GobblinScopeTypes.INSTANCE.defaultScopeInstance());
    Assert.assertEquals(jobBroker.getScope(GobblinScopeTypes.JOB).getType(), GobblinScopeTypes.JOB);
    Assert.assertEquals(jobBroker.getScope(GobblinScopeTypes.JOB).getClass(), JobScopeInstance.class);
    Assert.assertEquals(((JobScopeInstance) jobBroker.getScope(GobblinScopeTypes.JOB)).getJobId(), "job123");

    try {
      jobBroker.getScope(GobblinScopeTypes.TASK);
      Assert.fail();
    } catch (NoSuchScopeException nsse) {
      // should throw no scope exception
    }
  }

  @Test
  public void testLifecycle() throws Exception {
    Config config = ConfigFactory.empty();

    SharedResourcesBrokerImpl<GobblinScopeTypes> topBroker = SharedResourcesBrokerFactory.createDefaultTopLevelBroker(config,
        GobblinScopeTypes.GLOBAL.defaultScopeInstance());
    SharedResourcesBrokerImpl<GobblinScopeTypes> jobBroker =
        topBroker.newSubscopedBuilder(new JobScopeInstance("myJob", "job123")).build();
    SharedResourcesBrokerImpl<GobblinScopeTypes>
        containerBroker = topBroker.newSubscopedBuilder(GobblinScopeTypes.CONTAINER.defaultScopeInstance()).build();
    SharedResourcesBrokerImpl<GobblinScopeTypes> taskBroker = jobBroker.newSubscopedBuilder(new TaskScopeInstance("taskabc"))
        .withAdditionalParentBroker(containerBroker).build();

    // create a shared resource
    TestFactory.SharedResource jobResource =
        taskBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.JOB);
    TestFactory.SharedResource taskResource =
        taskBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.TASK);

    Assert.assertFalse(jobResource.isClosed());
    Assert.assertFalse(taskResource.isClosed());

    taskBroker.close();

    // only resources at lower scopes than task should be closed
    Assert.assertFalse(jobResource.isClosed());
    Assert.assertTrue(taskResource.isClosed());

    // since taskResource has been closed, broker should return a new instance of the object
    TestFactory.SharedResource taskResource2 =
        taskBroker.getSharedResourceAtScope(new TestFactory<GobblinScopeTypes>(), new TestResourceKey("myKey"), GobblinScopeTypes.TASK);
    Assert.assertNotEquals(taskResource, taskResource2);

    topBroker.close();

    Assert.assertTrue(jobResource.isClosed());
    Assert.assertTrue(taskResource.isClosed());
  }
}
