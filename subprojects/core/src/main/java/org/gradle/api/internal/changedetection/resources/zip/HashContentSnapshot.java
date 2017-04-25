/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.resources.zip;

import com.google.common.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.resource.ResourceContentMetadataSnapshot;

public class HashContentSnapshot implements ResourceContentMetadataSnapshot {
    private final FileType type;
    private final HashCode contentMd5;

    public HashContentSnapshot(FileType type, HashCode contentMd5) {
        this.type = type;
        this.contentMd5 = contentMd5;
    }

    @Override
    public FileType getType() {
        return type;
    }

    @Override
    public HashCode getContentMd5() {
        return contentMd5;
    }
}