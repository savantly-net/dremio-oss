/*
 * Copyright (C) 2017-2018 Dremio Corporation
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
package com.dremio.plugins.s3.store.copy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;

/**
 * (Copied from Hadoop 2.8.3, move to using Hadoop version once hadoop version in MapR profile is upgraded to 2.8.3+)
 *
 * A list of providers.
 *
 * This is similar to the AWS SDK {@code AWSCredentialsProviderChain},
 * except that:
 * <ol>
 *   <li>Allows extra providers to be added dynamically.</li>
 *   <li>If any provider in the chain throws an exception other than
 *   an {@link AmazonClientException}, that is rethrown, rather than
 *   swallowed.</li>
 *   <li>Has some more diagnostics.</li>
 *   <li>On failure, the last AmazonClientException raised is rethrown.</li>
 *   <li>Special handling of {@link AnonymousAWSCredentials}.</li>
 * </ol>
 */
public class AWSCredentialProviderList implements AWSCredentialsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AWSCredentialProviderList.class);

  public static final String NO_AWS_CREDENTIAL_PROVIDERS = "No AWS Credential Providers";

  private final List<AWSCredentialsProvider> providers = new ArrayList<>(1);
  private boolean reuseLastProvider = true;
  private AWSCredentialsProvider lastProvider;

  /**
   * Empty instance. This is not ready to be used.
   */
  public AWSCredentialProviderList() {
  }

  /**
   * Add a new provider.
   * @param p provider
   */
  public void add(AWSCredentialsProvider p) {
    providers.add(p);
  }

  /**
   * Refresh all child entries.
   */
  @Override
  public void refresh() {
    for (AWSCredentialsProvider provider : providers) {
      provider.refresh();
    }
  }

  /**
   * Iterate through the list of providers, to find one with credentials.
   * If {@link #reuseLastProvider} is true, then it is re-used.
   * @return a set of credentials (possibly anonymous), for authenticating.
   */
  @Override
  public AWSCredentials getCredentials() {
    checkNotEmpty();
    if (reuseLastProvider && lastProvider != null) {
      return lastProvider.getCredentials();
    }

    AmazonClientException lastException = null;
    for (AWSCredentialsProvider provider : providers) {
      try {
        AWSCredentials credentials = provider.getCredentials();
        if ((credentials.getAWSAccessKeyId() != null &&
            credentials.getAWSSecretKey() != null)
            || (credentials instanceof AnonymousAWSCredentials)) {
          lastProvider = provider;
          LOG.debug("Using credentials from {}", provider);
          return credentials;
        }
      } catch (AmazonClientException e) {
        lastException = e;
        LOG.debug("No credentials provided by {}: {}",
            provider, e.toString(), e);
      }
    }

    // no providers had any credentials. Rethrow the last exception
    // or create a new one.
    String message = "No AWS Credentials provided by "
        + listProviderNames();
    if (lastException != null) {
      message += ": " + lastException;
    }
    throw new AmazonClientException(message, lastException);

  }

  /**
   * Verify that the provider list is not empty.
   * @throws AmazonClientException if there are no providers.
   */
  public void checkNotEmpty() {
    if (providers.isEmpty()) {
      throw new AmazonClientException(NO_AWS_CREDENTIAL_PROVIDERS);
    }
  }

  /**
   * List all the providers' names.
   * @return a list of names, separated by spaces (with a trailing one).
   * If there are no providers, "" is returned.
   */
  public String listProviderNames() {
    StringBuilder sb = new StringBuilder(providers.size() * 32);
    for (AWSCredentialsProvider provider : providers) {
      sb.append(provider.getClass().getSimpleName());
      sb.append(' ');
    }
    return sb.toString();
  }

  /**
   * The string value is this class name and the string values of nested
   * providers.
   * @return a string value for debugging.
   */
  @Override
  public String toString() {
    return "AWSCredentialProviderList: " + StringUtils.join(providers, " ");
  }
}
