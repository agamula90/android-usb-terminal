package com.proggroup.areasquarecalculator.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import androidx.fragment.app.Fragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.utils.FloatFormatter;
import com.proggroup.areasquarecalculator.utils.IntentFolderWrapUtils;
import com.proggroup.areasquarecalculator.utils.ToastUtils;
import com.proggroup.CalculateExtensionsKt;

import java.io.File;

import fr.xgouchet.FileDialog;
import fr.xgouchet.SelectionMode;

public class CalculateSquareAreaFragment extends Fragment {

	private static final int SELECT_FILE_REQUEST_CODE = 0;

	private EditText mPathToFileEdit;

	private TextView mResult;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
			savedInstanceState) {
		return inflater.inflate(R.layout.fragment_calculate_square, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mPathToFileEdit = (EditText) view.findViewById(R.id.path_to_file);
		View selectPath = view.findViewById(R.id.select_path);
		selectPath.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity().getBaseContext(), FileDialog.class);
				intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory()
						.getAbsolutePath());
				intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);

				intent.putExtra(FileDialog.FORMAT_FILTER, new String[]{"csv"});

				IntentFolderWrapUtils.wrapFolderForDrawables(getActivity(), intent);

				startActivityForResult(intent, SELECT_FILE_REQUEST_CODE);
			}
		});

		View calculateSquare = view.findViewById(R.id.calculate_square);
		calculateSquare.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String pathToFile = mPathToFileEdit.getText().toString();
				File mInputFile = new File(pathToFile);

				if (mInputFile.exists()) {
					float res = CalculateExtensionsKt.calculateSquare(mInputFile);
					//res = CalculateUtils.calculateSquareDeterminant(mInputFile);
					//res = CalculateUtils.calculateSquareDeterminantParallel(mInputFile);
					mResult.setText(FloatFormatter.format(res));
				} else {
					Toast toast = Toast.makeText(getActivity(), getString(R.string.select_csv_data_file), Toast
							.LENGTH_LONG);
                    ToastUtils.wrap(toast);
					toast.show();
				}
			}
		});

		mResult = (TextView) view.findViewById(R.id.result);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == Activity.RESULT_OK && requestCode == SELECT_FILE_REQUEST_CODE) {
			mPathToFileEdit.setText(data.getStringExtra(FileDialog.RESULT_PATH));
		}
	}
}
