/*******************************************************************************
 * Copyright (c) 2010, 2012 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation of CVS adapter
 *     Gunnar Wagenknecht - initial API and implementation of Git adapter
 *     IBM Corporation - ongoing maintenance
 *******************************************************************************/
package org.eclipse.releng.tools.git;

import java.io.IOException;
import java.util.Calendar;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.osgi.util.NLS;
import org.eclipse.releng.tools.RelEngPlugin;
import org.eclipse.releng.tools.RepositoryProviderCopyrightAdapter;

public class GitCopyrightAdapter extends RepositoryProviderCopyrightAdapter {

	private static String filterString = "copyright"; // lowercase //$NON-NLS-1$

	public GitCopyrightAdapter(IResource[] resources) {
		super(resources);
	}

	@Override
	public int getLastModifiedYear(IFile file, IProgressMonitor monitor)
			throws CoreException {
		try {
			monitor.beginTask("Fetching logs from Git", 100); //$NON-NLS-1$
			final RepositoryMapping mapping = RepositoryMapping
					.getMapping(file);
			if (mapping != null) {
				final Repository repo = mapping.getRepository();
				if (repo != null) {
					RevWalk walk = null;
					try {
						final ObjectId start = repo.resolve(Constants.HEAD);
						walk = new RevWalk(repo);
						walk.setTreeFilter(AndTreeFilter.create(PathFilter
								.create(mapping.getRepoRelativePath(file)),
								TreeFilter.ANY_DIFF));
						walk.markStart(walk.lookupCommit(start));
						final RevCommit commit = walk.next();
						if (commit != null) {
							if (filterString != null
									&& commit.getFullMessage().toLowerCase()
											.indexOf(filterString) != -1) {
								// the last update was a copyright check in - ignore
								return 0;
							}

							boolean isSWT= file.getProject().getName().startsWith("org.eclipse.swt"); //$NON-NLS-1$
							String logComment= commit.getFullMessage();
							if (isSWT && (logComment.indexOf("restore HEAD after accidental deletion") != -1 || logComment.indexOf("fix permission of files") != -1)) { //$NON-NLS-1$ //$NON-NLS-2$
								// ignore commits with above comments
								return 0;
							}

							boolean isPlatform= file.getProject().getName().equals("eclipse.platform"); //$NON-NLS-1$
							if (isPlatform && (logComment.indexOf("Merge in ant and update from origin/master") != -1 || logComment.indexOf("Fixed bug 381684: Remove update from repository and map files") != -1 || logComment.indexOf("Restored 'org.eclipse.update.core'") != -1)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
								// ignore commits with above comments
								return 0;
							}


							final Calendar calendar = Calendar.getInstance();
							calendar.setTimeInMillis(0);
							calendar.add(Calendar.SECOND,
									commit.getCommitTime());
							return calendar.get(Calendar.YEAR);
						}
					} catch (final IOException e) {
						throw new CoreException(new Status(IStatus.ERROR,
								RelEngPlugin.ID, 0, NLS.bind(
										"An error occured when processing {0}",
										file.getName()), e));
					} finally {
						if (walk != null)
							walk.close();
					}
				}
			}
		} finally {
			monitor.done();
		}
		return -1;
	}

	@Override
	public void initialize(IProgressMonitor monitor) throws CoreException {
		// TODO We should perform a bulk "log" command to get the last modified
		// year
	}

}
