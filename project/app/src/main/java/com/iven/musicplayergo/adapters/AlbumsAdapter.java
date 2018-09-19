package com.iven.musicplayergo.adapters;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.iven.musicplayergo.R;
import com.iven.musicplayergo.models.Album;
import com.iven.musicplayergo.playback.PlayerAdapter;

import java.util.List;

public class AlbumsAdapter extends RecyclerView.Adapter<AlbumsAdapter.SimpleViewHolder> {

    private final Activity mActivity;
    private final PlayerAdapter mPlayerAdapter;
    private final AlbumSelectedListener mAlbumSelectedListener;
    private final List<Album> mAlbums;
    private Album mSelectedAlbum;

    public AlbumsAdapter(@NonNull final Activity activity, @NonNull final PlayerAdapter playerAdapter, @NonNull final List<Album> albums, final boolean showPlayedArtist) {
        mActivity = activity;
        mPlayerAdapter = playerAdapter;
        mAlbums = albums;
        mAlbumSelectedListener = (AlbumSelectedListener) mActivity;
        mSelectedAlbum = showPlayedArtist ? mPlayerAdapter.getPlayedAlbum() : mPlayerAdapter.getNavigationAlbum() != null ? mPlayerAdapter.getNavigationAlbum() : mAlbums.get(0);
        mAlbumSelectedListener.onAlbumSelected(mSelectedAlbum);
    }

    @Override
    @NonNull
    public SimpleViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {

        final View itemView = LayoutInflater.from(mActivity)
                .inflate(R.layout.album_item, parent, false);

        return new SimpleViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final SimpleViewHolder holder, final int position) {

        final Album album = mAlbums.get(holder.getAdapterPosition());
        final String albumTitle = album.getTitle();
        holder.title.setText(albumTitle);
        holder.year.setText(Album.getYearForAlbum(mActivity, album.getYear()));
        final int randomColor = ContextCompat.getColor(mActivity, ColorsAdapter.getRandomColor());
        holder.container.setCardBackgroundColor(ColorUtils.setAlphaComponent(randomColor, 25));
        holder.year.setTextColor(randomColor);
    }

    @Override
    public int getItemCount() {
        return mAlbums.size();
    }

    public interface AlbumSelectedListener {
        void onAlbumSelected(@NonNull final Album album);
    }

    class SimpleViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        final TextView title, year;
        final CardView container;

        SimpleViewHolder(@NonNull final View itemView) {
            super(itemView);

            container = (CardView) itemView;
            title = itemView.findViewById(R.id.album);
            year = itemView.findViewById(R.id.year);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(@NonNull final View v) {

            //update songs list only if the album is updated
            if (mAlbums.get(getAdapterPosition()) != mSelectedAlbum) {
                mSelectedAlbum = mAlbums.get(getAdapterPosition());
                mPlayerAdapter.setNavigationAlbum(mSelectedAlbum);
                mAlbumSelectedListener.onAlbumSelected(mSelectedAlbum);
            }
        }
    }
}