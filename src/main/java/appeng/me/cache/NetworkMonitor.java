/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cache;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.ItemWatcher;
import com.gamerforea.ae.EventConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkMonitor<T extends IAEStack<T>> implements IMEMonitor<T>
{
	@Nonnull
	private static final Deque<NetworkMonitor<?>> GLOBAL_DEPTH = Lists.newLinkedList();

	@Nonnull
	private final GridStorageCache myGridCache;
	@Nonnull
	private final StorageChannel myChannel;
	@Nonnull
	private final IItemList<T> cachedList;
	@Nonnull
	// TODO gamerforEA code replace, old code:
	// private final Map<IMEMonitorHandlerReceiver<T>, Object> listeners = new HashMap<>();
	private final boolean concurrencyListeners = EventConfig.fixNetworkListenersConcurrency;
	private final Map<IMEMonitorHandlerReceiver<T>, Object> listeners = this.concurrencyListeners ? new ConcurrentHashMap<>() : new HashMap<>();

	private static final Object DUMMY_VERIFICATION_TOKEN = new Object();
	// TODO gamerforEA code end

	private boolean sendEvent = false;
	private boolean hasChanged = false;
	@Nonnegative
	private int localDepthSemaphore = 0;

	public NetworkMonitor(final GridStorageCache cache, final StorageChannel chan)
	{
		this.myGridCache = cache;
		this.myChannel = chan;
		this.cachedList = (IItemList<T>) chan.createList();
	}

	@Override
	public void addListener(final IMEMonitorHandlerReceiver<T> listener, Object verificationToken)
	{
		// TODO gamerforEA code start
		Objects.requireNonNull(listener, "listener must not be null");

		if (this.concurrencyListeners && verificationToken == null)
			verificationToken = DUMMY_VERIFICATION_TOKEN;
		// TODO gamerforEA code end

		this.listeners.put(listener, verificationToken);
	}

	@Override
	public boolean canAccept(final T input)
	{
		return this.getHandler().canAccept(input);
	}

	@Override
	public T extractItems(final T request, final Actionable mode, final BaseActionSource src)
	{
		if (mode == Actionable.SIMULATE)
			return this.getHandler().extractItems(request, mode, src);

		this.localDepthSemaphore++;
		final T leftover = this.getHandler().extractItems(request, mode, src);
		this.localDepthSemaphore--;

		if (this.localDepthSemaphore == 0)
			this.monitorDifference(request.copy(), leftover, true, src);

		return leftover;
	}

	@Override
	public AccessRestriction getAccess()
	{
		return this.getHandler().getAccess();
	}

	@Override
	public IItemList<T> getAvailableItems(final IItemList out)
	{
		return this.getHandler().getAvailableItems(out);
	}

	@Override
	public StorageChannel getChannel()
	{
		return this.getHandler().getChannel();
	}

	@Override
	public int getPriority()
	{
		return this.getHandler().getPriority();
	}

	@Override
	public int getSlot()
	{
		return this.getHandler().getSlot();
	}

	@Nonnull
	@Override
	public IItemList<T> getStorageList()
	{
		if (this.hasChanged)
		{
			this.hasChanged = false;
			this.cachedList.resetStatus();
			return this.getAvailableItems(this.cachedList);
		}

		return this.cachedList;
	}

	@Override
	public T injectItems(final T input, final Actionable mode, final BaseActionSource src)
	{
		if (mode == Actionable.SIMULATE)
			return this.getHandler().injectItems(input, mode, src);

		this.localDepthSemaphore++;
		final T leftover = this.getHandler().injectItems(input, mode, src);
		this.localDepthSemaphore--;

		if (this.localDepthSemaphore == 0)
			this.monitorDifference(input.copy(), leftover, false, src);

		return leftover;
	}

	@Override
	public boolean isPrioritized(final T input)
	{
		return this.getHandler().isPrioritized(input);
	}

	@Override
	public void removeListener(final IMEMonitorHandlerReceiver<T> l)
	{
		this.listeners.remove(l);
	}

	@Override
	public boolean validForPass(final int i)
	{
		return this.getHandler().validForPass(i);
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private IMEInventoryHandler<T> getHandler()
	{
		switch (this.myChannel)
		{
			case ITEMS:
				return (IMEInventoryHandler<T>) this.myGridCache.getItemInventoryHandler();
			case FLUIDS:
				return (IMEInventoryHandler<T>) this.myGridCache.getFluidInventoryHandler();
			default:
		}
		return null;
	}

	private Iterator<Entry<IMEMonitorHandlerReceiver<T>, Object>> getListeners()
	{
		return this.listeners.entrySet().iterator();
	}

	private T monitorDifference(final IAEStack original, final T leftOvers, final boolean extraction, final BaseActionSource src)
	{
		final T diff = (T) original.copy();

		if (extraction)
			diff.setStackSize(leftOvers == null ? 0 : -leftOvers.getStackSize());
		else if (leftOvers != null)
			diff.decStackSize(leftOvers.getStackSize());

		if (diff.getStackSize() != 0)
			this.postChangesToListeners(ImmutableList.of(diff), src);

		return leftOvers;
	}

	private void notifyListenersOfChange(final Iterable<T> diff, final BaseActionSource src)
	{
		this.hasChanged = true;
		final Iterator<Entry<IMEMonitorHandlerReceiver<T>, Object>> i = this.getListeners();

		while (i.hasNext())
		{
			final Entry<IMEMonitorHandlerReceiver<T>, Object> o = i.next();
			final IMEMonitorHandlerReceiver<T> receiver = o.getKey();
			Object verificationToken = o.getValue();

			// TODO gamerforEA code start
			if (verificationToken == DUMMY_VERIFICATION_TOKEN)
				verificationToken = null;
			// TODO gamerforEA code end

			if (receiver.isValid(verificationToken))
				receiver.postChange(this, diff, src);
			else
				i.remove();
		}
	}

	private void postChangesToListeners(final Iterable<T> changes, final BaseActionSource src)
	{
		this.postChange(true, changes, src);
	}

	protected void postChange(final boolean add, final Iterable<T> changes, final BaseActionSource src)
	{
		if (this.localDepthSemaphore > 0 || GLOBAL_DEPTH.contains(this))
			return;

		GLOBAL_DEPTH.push(this);
		this.localDepthSemaphore++;

		this.sendEvent = true;

		this.notifyListenersOfChange(changes, src);

		for (final T changedItem : changes)
		{
			T difference = changedItem;

			if (!add && changedItem != null)
			{
				difference = changedItem.copy();
				difference.setStackSize(-changedItem.getStackSize());
			}

			if (this.myGridCache.getInterestManager().containsKey(changedItem))
			{
				final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(changedItem);

				if (!list.isEmpty())
				{
					// TODO gamerforEA code replace, old code:
					// IAEStack fullStack = this.getStorageList().findPrecise(changedItem);
					IItemList<T> storageList = this.getStorageList();
					IAEStack fullStack = storageList.findPrecise(changedItem);

					boolean optimize = EventConfig.optimizeNetworkPostChange;
					if (!optimize)
						storageList = null;
					// TODO gamerforEA code end

					if (fullStack == null)
					{
						fullStack = changedItem.copy();
						fullStack.setStackSize(0);
					}

					this.myGridCache.getInterestManager().enableTransactions();

					for (final ItemWatcher iw : list)
					{
						// TODO gamerforEA code replace, old code:
						// iw.getHost().onStackChange(this.getStorageList(), fullStack, difference, src, this.getChannel());
						if (optimize)
						{
							if (storageList == null)
								storageList = this.getStorageList();
							boolean storageListDirty = iw.getHost().onStackChangeAdv(storageList, fullStack, difference, src, this.getChannel());
							if (storageListDirty)
								storageList = null;
						}
						else
							iw.getHost().onStackChange(this.getStorageList(), fullStack, difference, src, this.getChannel());
						// TODO gamerforEA code end
					}

					this.myGridCache.getInterestManager().disableTransactions();
				}
			}
		}

		final NetworkMonitor<?> last = GLOBAL_DEPTH.pop();
		this.localDepthSemaphore--;

		if (last != this)
			throw new IllegalStateException("Invalid Access to Networked Storage API detected.");
	}

	void forceUpdate()
	{
		this.hasChanged = true;

		final Iterator<Entry<IMEMonitorHandlerReceiver<T>, Object>> i = this.getListeners();
		while (i.hasNext())
		{
			final Entry<IMEMonitorHandlerReceiver<T>, Object> o = i.next();
			final IMEMonitorHandlerReceiver<T> receiver = o.getKey();
			Object verificationToken = o.getValue();

			// TODO gamerforEA code start
			if (verificationToken == DUMMY_VERIFICATION_TOKEN)
				verificationToken = null;
			// TODO gamerforEA code end

			if (receiver.isValid(verificationToken))
				receiver.onListUpdate();
			else
				i.remove();
		}
	}

	void onTick()
	{
		if (this.sendEvent)
		{
			this.sendEvent = false;
			this.myGridCache.getGrid().postEvent(new MENetworkStorageEvent(this, this.myChannel));
		}
	}

}