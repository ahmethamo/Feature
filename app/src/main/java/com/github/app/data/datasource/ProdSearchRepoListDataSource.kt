package com.github.app.data.datasource

import androidx.lifecycle.MutableLiveData
import androidx.paging.ItemKeyedDataSource
import com.github.app.data.repository.mapper.mapToSearchListEntity
import com.github.app.data.source.remote.api.GithubApi
import com.github.app.domain.entity.SearchRepo
import com.github.app.domain.repository.RepoListDataSource
import com.github.app.extension.doInBackground
import com.github.app.util.Keys
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Action
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers

class ProdSearchRepoListDataSource(
    private val githubApi: GithubApi,
    private val compositeDisposable: CompositeDisposable
) : ItemKeyedDataSource<Int, SearchRepo>(),
    RepoListDataSource {

    private var retryCompletable: Completable? = null
    val networkState = MutableLiveData<NetworkState>()
    val initialLoad = MutableLiveData<NetworkState>()
    private var index: Int = Keys.PAGE

    override fun retry() {
        if (retryCompletable != null) {
            compositeDisposable.add(
                retryCompletable!!
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ }, { })
            )
        }
    }

    override fun loadInitial(
        params: LoadInitialParams<Int>,
        callback: LoadInitialCallback<SearchRepo>
    ) {
        networkState.postValue(NetworkState.LOADING)
        initialLoad.postValue(NetworkState.LOADING)
        compositeDisposable.add(
            githubApi
                .repositories(params.requestedLoadSize, index)
                .toObservable()
                .doInBackground()
                .subscribeBy(
                    onNext = {
                        setRetry(null)
                        networkState.postValue(NetworkState.LOADING)
                        initialLoad.postValue(NetworkState.LOADING)
                        callback.onResult(it.mapToSearchListEntity())
                    },
                    onError = {
                        val error = NetworkState.error(it.message)
                        networkState.postValue(error)
                        initialLoad.postValue(error)
                    })
        )
    }

    override fun loadAfter(params: LoadParams<Int>, callback: LoadCallback<SearchRepo>) {
        networkState.postValue(NetworkState.LOADING)
        compositeDisposable.add(
            githubApi
                .repositories(params.requestedLoadSize, ++index)
                .toObservable()
                .doInBackground()
                .subscribeBy(
                    onNext = {
                        setRetry(null)
                        networkState.postValue(NetworkState.LOADED)
                        callback.onResult(it.mapToSearchListEntity())
                    },
                    onError = {
                        setRetry(Action { loadAfter(params, callback) })
                        networkState.postValue(NetworkState.error(it.message))
                    })
        )
    }

    override fun loadBefore(params: LoadParams<Int>, callback: LoadCallback<SearchRepo>) {}

    override fun getKey(item: SearchRepo): Int = 0

    private fun setRetry(action: Action?) =
        action?.let { this.retryCompletable = Completable.fromAction(it) }
}