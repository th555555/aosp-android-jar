/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.appsearch.aidl;

import android.annotation.NonNull;
import android.app.appsearch.AppSearchResult;
import android.os.RemoteException;

/**
 * The base implementation class for {@link IAppSearchResultCallback}, which could extract {@link
 * AppSearchResult} from either {@link AppSearchResultParcel} or {@link AppSearchResultParcelV2}.
 *
 * @param <ValueType> The type of result object for successful calls.
 * @hide
 */
public abstract class AppSearchResultCallback<ValueType> extends IAppSearchResultCallback.Stub {

    /**
     * Invokes a callback function with an {@link AppSearchResult} that has been extracted from
     * either an {@link AppSearchResultParcel} or an {@link AppSearchResultParcelV2}.
     */
    public abstract void onResult(@NonNull AppSearchResult<ValueType> result);

    @Override
    public void onResult(AppSearchResultParcel appSearchResultParcel) throws RemoteException {
        onResult(appSearchResultParcel.getResult());
    }

    @Override
    public void onResultV2(AppSearchResultParcelV2 appSearchResultParcelV2) throws RemoteException {
        onResult(appSearchResultParcelV2.getResult());
    }
}
