package com.airbnb.chimas;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.functions.Action0;
import rx.subjects.ReplaySubject;
import rx.subscriptions.CompositeSubscription;

/**
 * This class is a middle man beween an {@link Observable} and an {@link Observer}. Since Retrofit
 * cancels upon unsubscription, this allows us to unsubscribe without cancelling the underlying
 * OkHttp call by using a proxy {@link ReplaySubject}. This is especially useful during activities
 * onPause() -> onResume(), where we want to avoid updating the UI but we still don't want to cancel
 * the request. This works like a lock/unlock events mechanism. It can be unlocked by calling
 * subscribe() again with the same Observable. Cancellation is usually more suited for lifecycle
 * events like Activity.onDestroy()
 */
final class SubscriptionProxy<T> {
  private final Observable<T> proxy;
  private final Subscription upstreamSubscription;
  private final CompositeSubscription subscriptionList;
  private Subscription subscription;

  private SubscriptionProxy(Observable<T> observable, Action0 onTerminate) {
    ReplaySubject<T> replaySubject = ReplaySubject.create();
    upstreamSubscription = observable.subscribe(replaySubject);
    proxy = replaySubject.doOnTerminate(onTerminate);
    subscriptionList = new CompositeSubscription(upstreamSubscription);
  }

  static <T> SubscriptionProxy<T> create(Observable<T> observable, Action0 onTerminate) {
    return new SubscriptionProxy<>(observable, onTerminate);
  }

  static <T> SubscriptionProxy<T> create(Observable<T> observable) {
    return new SubscriptionProxy<>(observable, DummyAction.instance());
  }

  Subscription subscribe(Observer<T> observer) {
    if (subscription != null) {
      unsubscribe();
    }
    subscription = proxy.subscribe(observer);
    subscriptionList.add(subscription);
    return subscription;
  }

  public void cancel() {
    subscriptionList.unsubscribe();
  }

  public void unsubscribe() {
    Preconditions.checkState(subscription != null, "Must call subscribe() first");
    subscription.unsubscribe();
  }

  public boolean isUnsubscribed() {
    return subscription != null && subscription.isUnsubscribed();
  }

  public boolean isCancelled() {
    return isUnsubscribed() && upstreamSubscription.isUnsubscribed();
  }
}
