package com.binjar.sample.app.movies

import androidx.annotation.NonNull
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.binjar.sample.app.core.AppViewModel
import com.binjar.sample.data.movie.MovieDataSource
import com.binjar.sample.data.movie.MoviePagedSource
import com.binjar.sample.data.movie.model.Movie
import com.binjar.sample.data.movie.model.SortBy
import com.binjar.sample.data.movie.paging.Listing
import io.reactivex.disposables.CompositeDisposable


class MovieViewModel private constructor(private val dataSource: MovieDataSource) : AppViewModel() {
    private val compositeDisposable = CompositeDisposable()

    private val movieQuery = MutableLiveData<Pair<String, SortBy>>()

    private val movieResult = Transformations.map(movieQuery) { query -> loadMovies(query.second) }

    val discoveredMovies = Transformations.switchMap(movieResult) { it.pagedList }
    val networkState = Transformations.switchMap(movieResult) { it.networkState }

    override fun refresh() {
        compositeDisposable.clear()
    }

    fun discover(sortBy: SortBy): Boolean {
        if (sortBy == movieQuery.value?.second) {
            return false
        }
        movieQuery.value = Pair("", sortBy)
        return true
    }

    private fun loadMovies(sortBy: SortBy): Listing<Movie> {
        val pagedSourceFactory = MoviePagedSource.Factory(compositeDisposable, dataSource, sortBy)
        val pagedConfig = PagedList.Config.Builder()
                .setEnablePlaceholders(false)
                .setPageSize(20)
                .setPrefetchDistance(5)
                .build()

        val pagedList = LivePagedListBuilder(pagedSourceFactory, pagedConfig).build()
        return Listing(
                pagedList = pagedList,
                networkState = Transformations.switchMap(pagedSourceFactory.movieDSLiveData) { it.networkState },
                refreshState = Transformations.switchMap(pagedSourceFactory.movieDSLiveData) { it.initialLoad },
                refresh = { pagedSourceFactory.movieDSLiveData.value?.invalidate() },
                retry = { pagedSourceFactory.movieDSLiveData.value?.retryAllFailed() }
        )
    }

    override fun onCleared() {
        compositeDisposable.clear()
        super.onCleared()
    }

    class Factory(private var dataSource: MovieDataSource) : ViewModelProvider.NewInstanceFactory() {

        @NonNull
        override fun <T : ViewModel> create(@NonNull modelClass: Class<T>): T {
            if (MovieViewModel::class.java.isAssignableFrom(modelClass)) {
                try {
                    return MovieViewModel(dataSource) as T
                } catch (e: Exception) {
                    throw RuntimeException("Cannot create an instance of \$modelClass", e)
                }

            }
            return super.create(modelClass)
        }
    }
}
