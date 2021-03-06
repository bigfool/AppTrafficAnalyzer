package me.ycdev.android.trafficanalyzer.snaps;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import me.ycdev.android.trafficanalyzer.R;
import me.ycdev.android.trafficanalyzer.profile.AppProfile;
import me.ycdev.android.trafficanalyzer.profile.AppProfilesMgr;
import me.ycdev.android.trafficanalyzer.usage.AppTrafficUsageActivity;
import me.ycdev.android.trafficanalyzer.utils.AppLogger;

public class SnapshotsListFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final boolean DEBUG = AppLogger.DEBUG;

    private ListView mListView;
    private SnapshotsAdapter mAdapter;

    private List<StatsSnapshot> mSnapshotItems;

    private ActionMode mActionMode;
    private MultiChoiceModeListener mMultiChoiceModeListener = new MultiChoiceModeListener() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.snapshot_context_menu, menu);
            updateTitle(mode); // for Activity recreation because of orientation change
            mActionMode = mode;
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.diff:
                    diffSnapshots();
                    return true;
                case R.id.edit:
                    editNotes();
                    return true;
                case R.id.delete:
                    deleteCheckedItems();
                    return true;

                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            updateTitle(mode);
            mAdapter.notifyDataSetChanged();
        }

        private void updateTitle(ActionMode mode) {
            int count = mListView.getCheckedItemCount();
            mode.setTitle(String.valueOf(count));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.snapshots_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View rootView = getView();

        mListView = (ListView) rootView.findViewById(R.id.snapshot_list);
        mListView.setEmptyView(rootView.findViewById(R.id.snapshot_empty_view));
        mAdapter = new SnapshotsAdapter(getActivity(), mListView);
        mListView.setAdapter(mAdapter);
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(mMultiChoiceModeListener);

        loadTrafficStatsSnapshot();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add:
                addTrafficStatsSnapshot();
                return true;

            case R.id.clear:
                clearTrafficStatsSnapshots();
                return true;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTrafficStatsSnapshot() {
        new MyTask(getString(R.string.tips_load_snapshot), new Runnable() {
            @Override
            public void run() {
                // force loading the snapshots
                StatsSnapshotsMgr.getInstance(getActivity()).getAllSnapshots();
            }
        }).execute();
    }

    private void addTrafficStatsSnapshot() {
        new MyTask(getString(R.string.tips_create_snapshot), new Runnable() {
            @Override
            public void run() {
                StatsSnapshotsMgr.getInstance(getActivity()).createSnapshot("");
            }
        }).execute();
    }

    private void clearTrafficStatsSnapshots() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dlg_title_warning);
        builder.setMessage(R.string.tips_clear_snapshots_confirm);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                doClearTrafficStatsSnapshots();
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void doClearTrafficStatsSnapshots() {
        new MyTask(getString(R.string.tips_clear_snapshots), new Runnable() {
            @Override
            public void run() {
                StatsSnapshotsMgr.getInstance(getActivity()).clearAllSnapshots();
            }
        }).execute();
    }

    private List<StatsSnapshot> getCheckedItems() {
        List<StatsSnapshot> results = new ArrayList<StatsSnapshot>();
        SparseBooleanArray checkedPositions = mListView.getCheckedItemPositions();
        for (int pos = 0; pos < mAdapter.getCount(); pos++) {
            if (checkedPositions.get(pos)) {
                results.add(mSnapshotItems.get(pos));
            }
        }
        return results;
    }

    private void diffSnapshots() {
        if (mListView.getCheckedItemCount() != 2) {
            Toast.makeText(getActivity(), R.string.tips_diff_item_count_error, Toast.LENGTH_LONG).show();
            return;
        }
        List<StatsSnapshot> checkedItems = getCheckedItems();
        StatsSnapshot oldSnapshot = checkedItems.get(0);
        StatsSnapshot newSnapshot = checkedItems.get(1);
        AppProfile profile = AppProfilesMgr.getInstance(getActivity()).getCurProfile();
        AppTrafficUsageActivity.showTrafficUsage(getActivity(), profile, oldSnapshot, newSnapshot);

        mListView.clearChoices();
        mActionMode.finish();
    }

    private void editNotes() {
        if (mListView.getCheckedItemCount() != 1) {
            Toast.makeText(getActivity(), R.string.tips_edit_item_count_error, Toast.LENGTH_LONG).show();
            return;
        }

        List<StatsSnapshot> checkedItems = getCheckedItems();
        final StatsSnapshot snapshot = checkedItems.get(0);

        View contentView = getActivity().getLayoutInflater().inflate(
                R.layout.snapshot_note_editor, null);
        final EditText editView = (EditText) contentView.findViewById(R.id.notes_input);
        editView.setText(snapshot.notes);
        editView.setSelectAllOnFocus(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dlg_title_edit);
        builder.setView(contentView);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                snapshot.notes = editView.getText().toString();
                saveSnapshotNotes(snapshot);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dlg = builder.create();
        dlg.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dlg.show();
    }

    private void saveSnapshotNotes(StatsSnapshot snapshot) {
        StatsSnapshotsMgr.getInstance(getActivity()).updateSnapshotNotes(snapshot);
        mSnapshotItems = StatsSnapshotsMgr.getInstance(getActivity()).getAllSnapshots();
        mAdapter.setData(mSnapshotItems);
    }

    private void deleteCheckedItems() {
        String msg = getString(R.string.tips_delete_snapshots_confirm, mListView.getCheckedItemCount());
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dlg_title_warning);
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                doDeleteCheckedItems();
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void doDeleteCheckedItems() {
        SparseBooleanArray checkedPositions = mListView.getCheckedItemPositions();
        ArrayList<StatsSnapshot> deleteItems = new ArrayList<StatsSnapshot>();
        final int N = checkedPositions.size();
        for (int i = 0; i < N; i++) {
            StatsSnapshot item = mSnapshotItems.get(checkedPositions.keyAt(i));
            deleteItems.add(item);
            if (DEBUG) AppLogger.d(TAG, "to delete: " + item.fileName);
        }
        StatsSnapshotsMgr.getInstance(getActivity()).deleteSnapshots(deleteItems);

        mSnapshotItems = StatsSnapshotsMgr.getInstance(getActivity()).getAllSnapshots();
        mAdapter.setData(mSnapshotItems);

        mListView.clearChoices();
        mActionMode.finish();
    }

    private class MyTask extends AsyncTask<Void, Void, Void> {
        private String mTips;
        private Runnable mTargetTask;
        private ProgressDialog mDialog;

        public MyTask(String tips, Runnable task) {
            mTips = tips;
            mTargetTask = task;
        }

        @Override
        protected void onPreExecute() {
            mDialog = new ProgressDialog(getActivity());
            mDialog.setMessage(mTips);
            mDialog.setCancelable(false);
            mDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            mTargetTask.run();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mDialog.dismiss();
            mSnapshotItems = StatsSnapshotsMgr.getInstance(getActivity()).getAllSnapshots();
            mAdapter.setData(mSnapshotItems);
        }
    }
}
