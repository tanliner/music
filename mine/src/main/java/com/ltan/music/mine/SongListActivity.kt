package com.ltan.music.mine

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.collection.LongSparseArray
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ltan.music.basemvp.BaseMVPActivity
import com.ltan.music.common.MusicLog
import com.ltan.music.common.ToastUtil
import com.ltan.music.mine.adapter.PlaceItemBinder
import com.ltan.music.mine.adapter.SongItemBinder
import com.ltan.music.mine.adapter.SongListHeaderBinder
import com.ltan.music.mine.beans.PlayListDetailRsp
import com.ltan.music.mine.beans.SongUrl
import com.ltan.music.mine.beans.Tracks
import com.ltan.music.mine.contract.ISongListContract
import com.ltan.music.mine.presenter.SongListPresenter
import com.ltan.music.service.MusicService
import com.ltan.music.widget.ClickType
import com.ltan.music.widget.ListItemClickListener
import com.ltan.music.widget.MusicPlayerController
import kotterknife.bindView
import me.drakeet.multitype.MultiTypeAdapter
import kotlin.math.min

/**
 * TMusic.com.ltan.music.mine
 *
 * @ClassName: SongListActivity
 * @Description:
 * @Author: tanlin
 * @Date:   2019-05-19
 * @Version: 1.0
 */
class SongListActivity : BaseMVPActivity<SongListPresenter>(), ISongListContract.View {

    companion object {
        const val ARG_SONG_ID = "song_list_id"
    }

    override fun initLayout(): Int {
        return R.layout.mine_activity_song_list
    }

    override fun initPresenter() {
        mPresenter.attachView(this)
    }

    private var mCurrentSong = SongItemObject()
    private var mServiceConn = PlayerConnection()
    private var mMusicBinder: MusicService.MyBinder? = null

    private var mSongListId: Long = 0L
    private val mSongsRcyView: RecyclerView by bindView(R.id.rcy_mine_song_list)
    private val mControllerView: MusicPlayerController by bindView(R.id.mmp_controller)
    private val mRcyAdapter = MultiTypeAdapter()

    // used to find the SongItem by SongUrls
    private val indexMap = LongSparseArray<SongItemObject>()

    private lateinit var mRcyItems: MutableList<Any>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processArgs()
        initView()
        querySongList()
        bindService(null)
    }

    private fun processArgs() {
        mSongListId = intent.getLongExtra(ARG_SONG_ID, 0L)
        mRcyItems = ArrayList()
        mRcyItems.add("header") // header button
        mRcyItems.add(PlaceItem()) // footer space
    }

    private fun initView() {
        val songItemBinder = SongItemBinder()
        val headerBinder = SongListHeaderBinder()
        headerBinder.setBackListener(object: SongListHeaderBinder.BackListener {
            override fun onBack() {
                finish()
            }
        })
        mRcyAdapter.register(String::class.java, headerBinder)
        mRcyAdapter.register(SongItemObject::class.java, songItemBinder)
        mRcyAdapter.register(PlaceItem::class.java, PlaceItemBinder())
        mRcyAdapter.items = mRcyItems

        songItemBinder.setOnItemClickListener(SongItemClick())
        mSongsRcyView.layoutManager = LinearLayoutManager(this)
        mSongsRcyView.adapter = mRcyAdapter
    }

    private fun querySongList() {
        mPresenter.getPlayListDetail(mSongListId)
    }

    private fun querySongUrls() {
        if (mRcyItems.size > 1) {
            val idsBuilder = StringBuilder()
            idsBuilder.append('[')
            for (i in 0 until min(mRcyItems.size - 1, 5)) {
                val songItemObject = mRcyItems[i] as SongItemObject
                idsBuilder.append(songItemObject.songId).append(',')
            }
            idsBuilder.deleteCharAt(idsBuilder.length - 1)
            idsBuilder.append(']')
            mPresenter.getSongUrl(idsBuilder.toString())
        }
    }

    override fun onPlayListDetail(data: PlayListDetailRsp?) {
        if (data?.playlist == null) {
            ToastUtil.showToastShort(getString(R.string.mine_play_list_failed))
            return
        }

        MusicLog.d(TAG, "view onPlayListDetail: ${data.playlist}")
        val tracks = data.playlist.tracks
        if (tracks != null) {
            for (i in 0 until tracks.size) {
                val item = SongItemObject()
                item.songId = tracks[i].id
                item.number = (i + 1)
                item.title = tracks[i].name
                item.subTitle = buildSubtitle(tracks[i])
                indexMap.put(item.songId, item)
                mRcyItems.add(mRcyItems.size - 1, item)
            }
        }
        mRcyAdapter.notifyDataSetChanged()
        querySongUrls()
    }

    override fun onSongUrl(songs: List<SongUrl>?) {
        if (songs.isNullOrEmpty()) {
            return
        }
        for (i in 0 until songs.size) {
            indexMap.get(songs[i].id)?.songUrl = songs[i].url
            MusicLog.v(TAG, "url returned: ${mCurrentSong.songId} vs ${songs[i].id} ${songs[i].url}")
            if(mCurrentSong.songId == songs[i].id) {
                mCurrentSong.songUrl = songs[i].url
                mCurrentSong.songUrl?.let { mMusicBinder?.play(it) }
            }
        }
    }

    private fun buildSubtitle(tracks: Tracks): String {
        val sb = StringBuilder()
        val ar = tracks.ar
        if (ar != null) {
            for (n in ar) {
                sb.append(n.name).append('/')
            }
            sb.deleteCharAt(sb.length - 1)
        }
        if (tracks.al != null) {
            sb.append(" - ").append(tracks.al.name)
        }
        return sb.toString()
    }

    private fun bindService(songUrl: String?) {
        // startService(Intent(this@SongListActivity.baseContext, MusicService::class.java))
        val intent = Intent(baseContext, MusicService::class.java)
        intent.putExtra("songUrl", songUrl)
        bindService(intent, mServiceConn, Service.BIND_AUTO_CREATE)
    }

    inner class SongItemClick : ListItemClickListener {
        val TAG = "SongItemClick"

        override fun onItemClick(position: Int, v: View, type: ClickType) {
            MusicLog.d(TAG, "item click $position $v $type")
            if(mMusicBinder == null) {
                MusicLog.w(TAG, "service bind error")
                return
            }

            val itemObject = mRcyItems[position] as SongItemObject
            val url = itemObject.songUrl
            if(url.isNullOrEmpty()) {
                mCurrentSong.songId = itemObject.songId
                mPresenter.getSongUrl(buildArgs(mCurrentSong.songId))
            } else {
                mCurrentSong.songId = -1
                mMusicBinder?.play(url)
            }
            ToastUtil.showToastShort("${itemObject.title} coming soon")
        }

        private fun buildArgs(songId: Long): String {
            return "[$songId]"
        }
    }

    inner class PlayerConnection : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            mMusicBinder = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            MusicLog.i(TAG, "service connected...")
            val binder = service as MusicService.MyBinder
            mMusicBinder = binder
            mMusicBinder?.setCallback(PlayerCallbackImpl(mControllerView))
            mControllerView.setPlayer(binder)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConn)
    }
}