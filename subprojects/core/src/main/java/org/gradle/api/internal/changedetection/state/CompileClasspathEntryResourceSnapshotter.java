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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.AbstractResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshottableReadableResource;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.resources.recorders.DefaultSnapshottingResultRecorder;
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.internal.Java9ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static com.google.common.base.Charsets.UTF_8;

public class CompileClasspathEntryResourceSnapshotter extends AbstractResourceSnapshotter {
    private final PersistentIndexedCache<HashCode, HashCode> signatureCache;
    private final HashCode snapshotterHash;
    private final StringInterner stringInterner;
    private final ApiClassExtractor apiClassExtractor = new ApiClassExtractor(Collections.<String>emptySet());
    private static final HashCode IGNORED = Hashing.md5().hashString("Ignored ABI", UTF_8);

    public CompileClasspathEntryResourceSnapshotter(PersistentIndexedCache<HashCode, HashCode> signatureCache, StringInterner stringInterner) {
        this.signatureCache = signatureCache;
        BuildCacheHasher hasher = new DefaultBuildCacheHasher();
        appendConfigurationToHasher(hasher);
        this.snapshotterHash = hasher.hash();
        this.stringInterner = stringInterner;
    }

    @Override
    protected void snapshotTree(SnapshottableResourceTree snapshottable, SnapshottingResultRecorder recorder) {
        throw new UnsupportedOperationException("Trees cannot be classpath entries");
    }

    private HashCode cacheKey(SnapshottableResource resource) {
        return Hashing.md5().newHasher().putBytes(snapshotterHash.asBytes()).putBytes(resource.getContent().getContentMd5().asBytes()).hash();
    }

    @Override
    protected void snapshotResource(SnapshottableResource resource, SnapshottingResultRecorder recorder) {
        if (resource instanceof SnapshottableReadableResource && resource.getName().endsWith(".class")) {
            if (resource instanceof SnapshottableFileSystemResource) {
                HashCode cacheKey = cacheKey(resource);
                HashCode signatureHash = signatureCache.get(cacheKey);
                if (signatureHash != null) {
                    if (!signatureHash.equals(IGNORED)) {
                        recorder.recordResult(resource, signatureHash);
                    }
                    return;
                }
            }
            hashClassSignature((SnapshottableReadableResource) resource, recorder);
        }
    }

    private void hashClassSignature(SnapshottableReadableResource resource, SnapshottingResultRecorder recorder) {
        // Use the ABI as the hash
        InputStream inputStream = null;
        try {
            inputStream = resource.read();
            hashApi(resource, ByteStreams.toByteArray(inputStream), recorder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private void hashApi(SnapshottableResource resource, byte[] classBytes, SnapshottingResultRecorder recorder) {
        try {
            Java9ClassReader reader = new Java9ClassReader(classBytes);
            if (apiClassExtractor.shouldExtractApiClassFrom(reader)) {
                byte[] signature = apiClassExtractor.extractApiClassFrom(reader);
                if (signature != null) {
                    HashCode signatureHash = Hashing.md5().hashBytes(signature);
                    recorder.recordResult(resource, signatureHash);
                    putToCache(resource, signatureHash);
                } else {
                    putToCache(resource, IGNORED);
                }
            } else {
                putToCache(resource, IGNORED);
            }
        } catch (Exception e) {
            HashCode contentsHash = Hashing.md5().hashBytes(classBytes);
            recorder.recordResult(resource, contentsHash);
            putToCache(resource, contentsHash);
            DeprecationLogger.nagUserWith("Malformed class file [" + resource.getName() + "] found on compile classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
        }
    }

    private void putToCache(SnapshottableResource resource, HashCode signatureHash) {
        if (resource instanceof SnapshottableFileSystemResource) {
            HashCode cacheKey = cacheKey(resource);
            signatureCache.put(cacheKey, signatureHash);
        }
    }

    @Override
    public SnapshottingResultRecorder createResultRecorder() {
        return new DefaultSnapshottingResultRecorder(TaskFilePropertySnapshotNormalizationStrategy.RELATIVE, TaskFilePropertyCompareStrategy.UNORDERED, stringInterner);
    }
}