/*
 * Created on Dec 5, 2011
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package it.md.littlethumb.activities;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import it.md.littlethumb.BuildInfo;
import it.md.littlethumb.R;

public class AboutActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		((TextView)findViewById(R.id.about_revision)).setText(BuildInfo.revision);
		((TextView)findViewById(R.id.about_date)).setText(BuildInfo.commitDate);
		((TextView)findViewById(R.id.about_url)).setText(Html.fromHtml("<a href=\""+BuildInfo.repositoryURL+"\">"+BuildInfo.repositoryURL+"</a>"));
		((TextView)findViewById(R.id.about_description)).setText(Html.fromHtml(this.getString(R.string.aboutText)));
		
		((TextView)findViewById(R.id.about_description)).setMovementMethod(LinkMovementMethod.getInstance());
		((TextView)findViewById(R.id.about_url)).setMovementMethod(LinkMovementMethod.getInstance());
		
	}
	
	@Override
	protected void onResume() {
		super.onResume();
//		log.debug("setting context");
		
	}

}
