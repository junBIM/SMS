package com.example.sms.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;

import com.example.sms.Contact;
import com.example.sms.Message;
import com.example.sms.MessageManager;
import com.example.sms.R;

public class MessageListActivity extends ListActivity implements OnItemClickListener, OnClickListener{
	private static final String TAG = "MessageListActivity";
	
	private Contact rec;
	private long cid;
	private List<Message> messages;
	// We need to keep track of draft, while user is editing it 
	//   update draft to sent in db, when draft is sent.
	//   update draft when this activity is destroyed.
	private boolean localDraft;
	private Message draft;

	private ImageButton btn_send;
	private EditText et_content;
	private MessageAdapter adapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_message_list);
		btn_send = (ImageButton) findViewById(R.id.btn_send);
		et_content = (EditText) findViewById(R.id.text_content);
		
		btn_send.setOnClickListener(this);
		et_content.addTextChangedListener(contentWatcher);

		// initialize conversation data
		Intent intent = getIntent();
		long cid = intent.getLongExtra("cid", 0);
		messages = MessageManager.getMessagesInCoversation(this, cid);
		draft = MessageManager.getDraftInCoversation(this, cid);
		
		// set all messages read
		setMessagesRead();
		
		// if there is no sent/received message
		// the draft can not be null
		if(messages.size()!=0){
			rec = messages.get(0).getContact();
		}
		else{
			rec = draft.getContact();
		}
		
		if(draft!=null){
			et_content.setText(draft.getContent());
			localDraft = false;
		}
		else{
			draft = new Message(-1, cid, "", Message.MESSAGE_TYPE_DRAFT, false, 0, null, rec);
			localDraft = true;
		}
		
		if(et_content.length()==0){
			btn_send.setEnabled(false);
		}
		// Show the up button
		ActionBar actionBar = this.getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		// Set activity title
		if(rec.getName()!=null){
			actionBar.setTitle(rec.getName());
			actionBar.setSubtitle(rec.getPhoneNumber());
		}
		else{
			actionBar.setTitle(rec.getPhoneNumber());
		}
			    
	    // associate data with listView
		adapter = new MessageAdapter(this, messages, MessageAdapter.TYPE_BUBLE);
		setListAdapter(adapter);
		getListView().setOnItemClickListener(this);
		getListView().requestFocus();
		getListView().setSelectionFromTop(adapter.getCount(), 0);
		registerForContextMenu(getListView());
	}

	@Override
	public void onDestroy(){
		if(!localDraft){
			if(draft.getContent().length()==0){
				draft.delete(this);
			}
			else{
				draft.setTimeStamp(System.currentTimeMillis());
				draft.update(this);
			}
		}
		else{
			if(draft.getContent().length()!=0){
				draft.setTimeStamp(System.currentTimeMillis());
				draft.insert(this);
			}
		}
		super.onDestroy();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.message_list_menu, menu);
		menu.getItem(1).setEnabled(rec.getName()==null);
		return true;
	}
	
	/**
	 *	Handle click on action bar 
	 * 
	 **/
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	        case R.id.action_call:
	        	String uri = "tel:" + rec.getPhoneNumber();
	        	Intent intent = new Intent(Intent.ACTION_DIAL);
	        	intent.setData(Uri.parse(uri));
	        	startActivity(intent);
	            return true;
	        case R.id.action_add_contact:
	        	// Ask stock contact manager to insert the new contact
	        	Intent in = new Intent(Intents.Insert.ACTION);
	        	in.setType(ContactsContract.RawContacts.CONTENT_TYPE);
	        	in.putExtra(Intents.Insert.PHONE, rec.getPhoneNumber());
	        	startActivity(in);
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
		show_message_detail(pos);
	}
	
	/// Floating contextual Menu ///
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	            ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.message_context_menu, menu);
		menu.setHeaderTitle("Message options");
	}

    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterView.AdapterContextMenuInfo info = 
    			(AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
    	final int pos = info.position;
    	
        switch (item.getItemId()) {
            case R.id.action_forward:
                forward_message(pos);
                return true;
            case R.id.action_delete:
            	// prompt alert dialog, ask user's confirmation
            	AlertDialog.Builder alert = new AlertDialog.Builder(MessageListActivity.this);
            	alert.setTitle("Alert");
            	alert.setMessage("Message will be permanently deleted! Are you sure to continue?");
            	// Delete //
            	alert.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            		public void onClick(DialogInterface dialog, int whichButton) {
                        delete_message(pos);
            		}
            	});
            	// Cancel //
            	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            		public void onClick(DialogInterface dialog, int whichButton) {
            		}
            	});
            	alert.show();
                return true;
            case R.id.action_detail:
            	show_message_detail(pos);
            	return true;
            default:
                return super.onContextItemSelected(item);
        }
    }
    ///END: Floating contextual Menu///
    
    private void forward_message(int position){
    	Message msg = (Message) getListView().getItemAtPosition(position);
        Intent intent = new Intent();
        intent.setClass(MessageListActivity.this, NewMessageActivity.class);        
        intent.putExtra("content", msg.getContent());
        startActivity(intent);
    }
    
    private void delete_message(int position){
    	Message msg = (Message) getListView().getItemAtPosition(position);
    	// update in database
    	msg.delete(this);
    	// update in UI
    	adapter.remove(position);
    	adapter.notifyDataSetChanged();
    	if(adapter.getCount()==0){
    		finish();
    	}
    }
    
    private void show_message_detail(int position){
		String detail = "";
		
		Message msg = (Message) getListView().getItemAtPosition(position);
		Date date = new Date(msg.getTimeStamp());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:m a");
        
		if(msg.fromMe()){
			detail += "To: "+rec.getPhoneNumber()+"\n";
			detail += "Sent: "+dateFormat.format(date);
		}
		else{
			detail += "From: "+rec.getPhoneNumber()+"\n";
			detail += "Received: "+dateFormat.format(date);
		}
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Message detials");
		alert.setMessage(detail);
		alert.show();
    }

	@Override
	public void onClick(View v) {
		if(v.equals(btn_send)){
			sendMessage();
			et_content.setText("");
			// hide the 
			InputMethodManager imm = 
					(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(et_content.getWindowToken(), 0);
		}
	}
	
	private TextWatcher contentWatcher = new TextWatcher(){
		@Override
		public void afterTextChanged(Editable etd) {
			draft.setContent(etd.toString());
			if(etd.length()==0){
				btn_send.setEnabled(false);
			}
			else{
				btn_send.setEnabled(true);
			}
		}
	
		@Override
		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {
		}
	
		@Override
		public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
			// TODO Auto-generated method stub
			
		}
	};
	
	private void sendMessage(){
		//TODO send message out
		MessageManager.sendMessage(draft);
		Message sentMsg = new Message(draft);
		if(localDraft){
			// insert the new sent message
			sentMsg.setType(Message.MESSAGE_TYPE_SENT);
			sentMsg.setTimeStamp(System.currentTimeMillis());
			sentMsg.insert(this);
		}
		else{
			// update draft to sent message
			sentMsg.setTimeStamp(System.currentTimeMillis());
			sentMsg.updateSentDraft(this);
			// now  we need a new local draft
			draft = new Message(-1, cid, "", Message.MESSAGE_TYPE_DRAFT, false, 0, null, rec);
			localDraft = true;
		}
		adapter.add(sentMsg);
		adapter.notifyDataSetChanged();
	};
	
	private void setMessagesRead(){
		for(Message msg:messages){
			if(msg.isUnread()){
				msg.setUnread(Message.SMS_READ);
				msg.update(this);
			}
		}
	}
}
