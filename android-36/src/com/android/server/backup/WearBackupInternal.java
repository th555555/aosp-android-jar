/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.backup;

import android.app.backup.BackupRestoreEventLogger;

import com.android.internal.annotations.Keep;

/** A local service internal for Wear OS handle backup/restore */
@Keep
public interface WearBackupInternal {

    /** Gets the backup payload */
    byte[] getBackupPayload(BackupRestoreEventLogger logger);

    /** Applies the restored payload */
    void applyRestoredPayload(byte[] payload);
}
