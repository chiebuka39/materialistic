/*
 * Copyright (c) 2016 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.hidroh.materialistic.data;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.util.ServiceController;

import java.util.List;

import javax.inject.Inject;

import io.github.hidroh.materialistic.R;
import io.github.hidroh.materialistic.TestApplication;
import io.github.hidroh.materialistic.test.TestRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(TestRunner.class)
public class ItemSyncJobServiceTest {
    private ServiceController<ItemSyncJobService> controller;
    private ItemSyncJobService service;
    @Inject SyncDelegate syncDelegate;
    @Captor ArgumentCaptor<SyncDelegate.ProgressListener> listenerCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestApplication.applicationGraph.inject(this);
        reset(syncDelegate);
        controller = Robolectric.buildService(ItemSyncJobService.class);
        service = controller.create().get();
    }

    @Test
    public void testScheduledJob() {
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.application)
                .edit()
                .putBoolean(RuntimeEnvironment.application.getString(R.string.pref_saved_item_sync), true)
                .apply();
        shadowOf((ConnectivityManager) RuntimeEnvironment.application
                .getSystemService(Context.CONNECTIVITY_SERVICE))
                .setActiveNetworkInfo(ShadowNetworkInfo.newInstance(null,
                        ConnectivityManager.TYPE_WIFI, 0, true, true));
        SyncDelegate.initSync(RuntimeEnvironment.application, "1");
        List<JobInfo> pendingJobs = shadowOf((JobScheduler) RuntimeEnvironment.application
                .getSystemService(Context.JOB_SCHEDULER_SERVICE)).getAllPendingJobs();
        assertThat(pendingJobs).isNotEmpty();
        JobInfo actual = pendingJobs.get(0);
        assertThat(actual.getService().getClassName())
                .isEqualTo(ItemSyncJobService.class.getName());
        assertThat(actual.getExtras().getString(ItemSyncJobService.EXTRA_ID)).contains("1");
    }

    @Test
    public void testStartJob() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ItemSyncJobService.EXTRA_ID, "1");
        JobParameters jobParameters = mock(JobParameters.class);
        when(jobParameters.getExtras()).thenReturn(bundle);
        service.onStartJob(jobParameters);
        verify(syncDelegate).subscribe(listenerCaptor.capture());
        verify(syncDelegate).performSync(any(SyncDelegate.Job.class));
        listenerCaptor.getValue().onDone("1");
    }

    @After
    public void tearDown() {
        controller.destroy();
    }
}
