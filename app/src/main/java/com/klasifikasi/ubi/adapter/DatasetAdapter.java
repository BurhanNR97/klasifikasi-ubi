package com.klasifikasi.ubi.adapter;

import android.annotation.SuppressLint;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.model.DatasetItem;
import com.klasifikasi.ubi.utils.UrlUtil;
import java.util.*;

public class DatasetAdapter extends RecyclerView.Adapter<DatasetAdapter.VH> {

    public interface Listener {
        default void onClick(DatasetItem d) {}
        default void onDownload(DatasetItem d) {}
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
        h.tvNama.setText("("+d.id+") " + d.nama);
        h.tvTanggal.setText(d.tanggal);
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
