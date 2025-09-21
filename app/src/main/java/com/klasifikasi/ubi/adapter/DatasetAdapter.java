package com.klasifikasi.ubi.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.model.DatasetItem;

import java.util.ArrayList;
import java.util.List;

public class DatasetAdapter extends RecyclerView.Adapter<DatasetAdapter.VH> {

    @FunctionalInterface
    public interface Listener {
        // Satu-satunya method abstrak → bisa pakai lambda
        void onDownload(DatasetItem d);

        // Opsional
        default void onClick(DatasetItem d) {}
    }

    private final List<DatasetItem> data = new ArrayList<>();
    private final Listener listener;

    public DatasetAdapter(Listener l){ this.listener = l; }

    public void setData(List<DatasetItem> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int vtype) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_dataset, p, false);
        return new VH(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DatasetItem d = data.get(pos);

        String nama = d.nama != null ? d.nama : "(tanpa nama)";
        String tanggal = d.tanggal != null ? d.tanggal : "";

        h.tvNama.setText("(" + d.id + ") " + nama);
        h.tvTanggal.setText(tanggal);

        h.btnDownload.setOnClickListener(v -> listener.onDownload(d));
        h.itemView.setOnClickListener(v -> listener.onClick(d));
    }

    @Override public int getItemCount(){ return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNama, tvTanggal; ImageButton btnDownload;
        VH(@NonNull View v){
            super(v);
            tvNama = v.findViewById(R.id.tvNama);
            tvTanggal = v.findViewById(R.id.tvTanggal);
            btnDownload = v.findViewById(R.id.btnDownload);
        }
    }
}
