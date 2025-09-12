package com.klasifikasi.ubi.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.klasifikasi.ubi.R;
import com.klasifikasi.ubi.model.Sampel;
import com.klasifikasi.ubi.net.ApiConst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SampelAdapter extends RecyclerView.Adapter<SampelAdapter.VH> {

    public interface Listener {
        void onEdit(Sampel s);
        void onDelete(Sampel s);
        default void onClickItem(Sampel s) {}
    }

    private final List<Sampel> data = new ArrayList<>();
    private final Listener listener;

    public SampelAdapter(Listener l) {
        this.listener = l;
        setHasStableIds(true);
    }

    public void setData(List<Sampel> list) {
        List<Sampel> newList = (list == null) ? Collections.emptyList() : new ArrayList<>(list);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(data, newList));
        data.clear();
        data.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    public void removeItem(Sampel s) {
        int idx = indexOfId(s.id);
        if (idx >= 0) {
            data.remove(idx);
            notifyItemRemoved(idx);
        }
    }

    public void updateItem(Sampel s) {
        int idx = indexOfId(s.id);
        if (idx >= 0) {
            data.set(idx, s);
            notifyItemChanged(idx);
        }
    }

    public Sampel getItem(int position) { return data.get(position); }

    private int indexOfId(int id) {
        for (int i = 0; i < data.size(); i++) if (data.get(i).id == id) return i;
        return -1;
    }

    @Override public long getItemId(int position) { return data.get(position).id; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sampel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Sampel s = data.get(pos);

        h.tvKode.setText(s.kd_sampel);
        h.tvJenis.setText(s.jenis);
        h.tvTanggal.setText(s.tanggal);

        String url = buildFotoUrl(s.foto);
        Log.d("IMG_URL", "-> " + url);

        if (url == null || url.isEmpty()) {
            Glide.with(h.img.getContext()).clear(h.img);
            h.img.setImageResource(android.R.drawable.ic_menu_report_image);
        } else {
            Glide.with(h.img.getContext())
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .centerCrop()
                    .into(h.img);
        }

        h.itemView.setOnClickListener(v -> listener.onClickItem(s));
        h.btnEdit.setOnClickListener(v -> listener.onEdit(s));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(s));
    }

    @Override public int getItemCount() { return data.size(); }

    /** Normalisasi URL dari field 'foto' (contoh nilai: "storage/sampel/abc.jpg") */
    private static String buildFotoUrl(String path) {
        if (path == null) return null;
        String f = path.trim();
        if (f.isEmpty()) return null;

        // Jika sudah full URL, pakai langsung
        if (f.startsWith("http://") || f.startsWith("https://")) return f;

        // buang leading slash jika ada
        if (f.startsWith("/")) f = f.substring(1);

        String base = ApiConst.BASE_FILE_URL; // contoh: "http://147.93.81.231/" atau "https://domain/"
        if (base == null || base.isEmpty()) return f;
        if (!base.endsWith("/")) base = base + "/";
        return base + f; // hasil: http(s)://host/storage/...
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private final List<Sampel> oldList, newList;
        DiffCallback(List<Sampel> oldList, List<Sampel> newList) { this.oldList = oldList; this.newList = newList; }
        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }
        @Override public boolean areItemsTheSame(int o, int n) { return oldList.get(o).id == newList.get(n).id; }
        @Override public boolean areContentsTheSame(int o, int n) {
            Sampel a = oldList.get(o), b = newList.get(n);
            return Objects.equals(a.kd_sampel, b.kd_sampel)
                    && Objects.equals(a.jenis, b.jenis)
                    && Objects.equals(a.tanggal, b.tanggal)
                    && Objects.equals(a.foto, b.foto);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView tvKode, tvJenis, tvTanggal; ImageButton btnEdit, btnDelete;
        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.imgFoto);
            tvKode = v.findViewById(R.id.tvKode);
            tvJenis = v.findViewById(R.id.tvJenis);
            tvTanggal = v.findViewById(R.id.tvTanggal);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}