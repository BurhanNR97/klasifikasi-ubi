package com.klasifikasi.ubi.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.model.Hasil;
import com.klasifikasi.ubi.net.ApiConst;

import java.util.ArrayList;
import java.util.List;

public class HasilAdapter extends RecyclerView.Adapter<HasilAdapter.VH> {

    public interface Listener {
        void onView(Hasil h);
        void onDelete(Hasil h);
    }

    private final List<Hasil> data = new ArrayList<>();
    private final Listener listener;

    public HasilAdapter(Listener l) { this.listener = l; }

    public void setData(List<Hasil> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hasil, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Hasil it = data.get(pos);

        // Gambar
        Glide.with(h.itemView.getContext())
                .load(ApiConst.BASE_FILE_URL + it.getUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(h.thumb);

        Log.d("url foto", it.getUrl());

        // Text
        h.tvWarna.setText(it.getWarna() == null ? "-" : "Warna: " + it.getWarna());
        h.tvTekstur.setText(it.getTekstur() == null ? "-" : "Tekstur: " + it.getTekstur());
        h.tvGabungan.setText(it.getGabungan() == null ? "-" : "Gabungan: " + it.getGabungan());
        h.tvTanggal.setText(it.getTanggal() == null ? "-" : "Waktu: " + it.getTanggal());

        // Aksi
        h.btnLihat.setOnClickListener(v -> { if (listener != null) listener.onView(it); });
        h.btnHapus.setOnClickListener(v -> { if (listener != null) listener.onDelete(it); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView tvWarna, tvTekstur, tvGabungan, tvTanggal;
        ImageButton btnLihat, btnHapus;
        VH(@NonNull View v) {
            super(v);
            thumb     = v.findViewById(R.id.imgThumb);
            tvWarna   = v.findViewById(R.id.tvWarna);
            tvTekstur = v.findViewById(R.id.tvTekstur);
            tvGabungan= v.findViewById(R.id.tvGabungan);
            tvTanggal = v.findViewById(R.id.tvTanggal);
            btnLihat  = v.findViewById(R.id.btnLihat);
            btnHapus  = v.findViewById(R.id.btnHapus);
        }
    }
}
