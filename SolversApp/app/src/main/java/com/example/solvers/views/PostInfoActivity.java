package com.example.solvers.views;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.solvers.R;
import com.example.solvers.adapters.AnswersRecyclerViewAdapter;
import com.example.solvers.adapters.PostRecyclerViewAdapter;
import com.example.solvers.models.Answer;
import com.example.solvers.models.Post;
import com.example.solvers.models.User;
import com.example.solvers.utils.MarkwonBuilder;
import com.example.solvers.views.fragments.HomeFragment;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.w3c.dom.Document;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import agency.tango.android.avatarview.IImageLoader;
import agency.tango.android.avatarview.loader.PicassoLoader;
import agency.tango.android.avatarview.views.AvatarView;
import io.noties.markwon.Markwon;
import io.noties.markwon.editor.MarkwonEditor;
import io.noties.markwon.editor.MarkwonEditorTextWatcher;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;

public class PostInfoActivity extends AppCompatActivity {

    private Post post;
    private User author;

    Toolbar toolbar;
    TextView txtDescInfo,txtAuthorName;
    AvatarView imgAuthor;

    AvatarView imgUser;
    EditText txtUserAnswer;
    FloatingActionButton fabSend;

    RecyclerView recyclerView;
    AnswersRecyclerViewAdapter answersAdapter;

    private FirebaseFirestore db;

    private LinkedList<Answer> answerList = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_info);

        //Init Firebase instances
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        //Get post id from HomeFragment
        Intent intent = getIntent();
        String postId = intent.getStringExtra(HomeFragment.TAG);

        //Set toolbar return button
        toolbar = findViewById(R.id.toolbar_info);
        toolbar.setNavigationOnClickListener(view -> PostInfoActivity.this.finish());

        txtDescInfo = findViewById(R.id.txt_desc_info);
        txtAuthorName = findViewById(R.id.author_name);
        imgAuthor = findViewById(R.id.author_photo);

        txtUserAnswer = findViewById(R.id.txt_user_answer);
        imgUser = findViewById(R.id.user_avatar);
        fabSend = findViewById(R.id.bt_answer_send);

        //Init Markdown interpreter and editor
        final Markwon markwon = MarkwonBuilder.build(this, txtUserAnswer.getTextSize());
        final MarkwonEditor editor = MarkwonEditor.create(markwon);
        txtUserAnswer.addTextChangedListener(MarkwonEditorTextWatcher.withPreRender(
                editor,
                Executors.newCachedThreadPool(),
                txtUserAnswer));

        //Get posts
        DocumentReference postRef = getPostRef(postId);
        postRef.get().addOnCompleteListener(task -> {
            if(task.isSuccessful()){
                DocumentSnapshot doc = task.getResult();
                if(doc.exists()){

                    HashMap<String, Object> postHash = (HashMap<String, Object>) doc.getData();
                    postHash.put("id",doc.getId());
                    post = new Post(postHash);

                    if(user.getPhotoUrl()!=null&&!String.valueOf(user.getPhotoUrl()).equals("")){
                        IImageLoader imgLoader = new PicassoLoader();
                        imgLoader.loadImage(imgUser, String.valueOf(user.getPhotoUrl()),user.getDisplayName());
                    }

                    markwon.setMarkdown(txtDescInfo, post.getDescription());

                    buildToolbar();

                    DocumentReference authorRef = getAuthorRef(post.getAuthor());
                    getAuthor(authorRef);

                    getAnswers(postRef).addSnapshotListener((value, e) -> answerListener(value,e));
                }
            }
        });

        //Submit post answer
        fabSend.setOnClickListener(view -> {
            String txtAnswer = txtUserAnswer.getText().toString();
            if(!txtAnswer.equals("")){
                Date nowDate = new Date();

                Map<String,Object> answerHash = new HashMap<>();
                answerHash.put("author",getAuthorRef(user.getUid()));
                answerHash.put("post",postRef);
                answerHash.put("text",txtAnswer);
                answerHash.put("createdAt",nowDate);

                answerQuestion(answerHash);

                Log.d("answer",answerHash.toString());
            }
        });
    }

    private void buildRecyclerView(){
        recyclerView = findViewById(R.id.answers_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        answersAdapter = new AnswersRecyclerViewAdapter(answerList,getApplicationContext());

        recyclerView.setAdapter(answersAdapter);
    }

    public DocumentReference getPostRef(String id){
        return db.collection("posts").document(id);
    }

    public DocumentReference getAuthorRef(String uid) {
        return db.collection("users").document(uid);
    }

    public Query getAnswers(DocumentReference postRef){
        return db.collection("answers")
                .whereEqualTo("post",postRef)
                .orderBy("createdAt", Query.Direction.ASCENDING);
    }

    public void getAuthor(DocumentReference authorRef){
        authorRef.get().addOnCompleteListener(task1 -> {
            if (task1.isSuccessful()){
                DocumentSnapshot doc1 = task1.getResult();

                if(doc1.exists()){
                    HashMap<String,Object> authorHash = (HashMap<String, Object>) doc1.getData();
                    Log.d("author",authorHash.toString());
                    author = new User(authorHash);

                    if(author.getImageUrl()!=null && !author.getImageUrl().equals("")){
                        IImageLoader imgLoader = new PicassoLoader();
                        imgLoader.loadImage(imgAuthor,author.getImageUrl(),author.getName());
                    }

                    if(author.getName()!=null && !author.getName().equals("")){
                        txtAuthorName.setText(author.getName());
                    }
                }
            }
        });
    }

    public void addAnswer(QueryDocumentSnapshot doc){
        if(doc.get("text") != null){
            HashMap<String, Object> answerHash = (HashMap<String, Object>) doc.getData();
            answerHash.put("id",doc.getId());

            Answer answer = new Answer(answerHash);

            answerList.add(answer);
        }
    }

    public void modifyAnswer(QueryDocumentSnapshot doc){
        if(doc.get("description") != null){
            HashMap<String, Object> answerHash = (HashMap<String, Object>) doc.getData();
            answerHash.put("id",doc.getId());

            Answer answer = new Answer(answerHash);

            int index = 0;
            for(Answer answer_data : answerList){
                if(answer_data.getId().equals(answer.getId())){
                    answerList.set(index, answer);
                }
                index++;
            }
        }
    }

    public void deleteAnswer(QueryDocumentSnapshot doc){
        if(doc.get("description") != null){
            HashMap<String, Object> answerHash = (HashMap<String, Object>) doc.getData();
            answerHash.put("id",doc.getId());

            Answer answer = new Answer(answerHash);

            int index = 0;
            for(Answer answer_data : answerList){
                if(answer_data.getId().equals(answer.getId())){
                    answerList.remove(index);
                }
                index++;
            }
        }
    }

    public void answerQuestion(Map<String,Object> answerHash){
        db.collection("answers").add(answerHash).addOnCompleteListener(task3 -> {
            if (task3.isSuccessful()){
                Log.d("answer", "Successful answered");
                txtUserAnswer.setText("");
            }
        });
    }

    public void answerListener(QuerySnapshot value, FirebaseFirestoreException e){
        if (e != null) {
            Log.e("error", "listen:error", e);
            return;
        }

        for (DocumentChange dc : value.getDocumentChanges()) {
            switch (dc.getType()) {
                case ADDED:
                    Log.d("post", "New post: " + dc.getDocument().getData());
                    addAnswer(dc.getDocument());
                    break;
                case MODIFIED:
                    Log.d("post", "Modified post: " + dc.getDocument().getData());
                    modifyAnswer(dc.getDocument());
                    break;
                case REMOVED:
                    Log.d("post", "Removed post: " + dc.getDocument().getData());
                    deleteAnswer(dc.getDocument());
                    break;
            }
        }
        Log.d("answer", "Answer List: "+answerList.toString());
        buildRecyclerView();
    }

    public void buildToolbar(){
        toolbar.setTitle(post.getSubject());

        //Get the difference between now and post date
        Date now = new Date();
        long diff = now.getTime() - post.getCreatedAt().toDate().getTime();
        long diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diff);

        //Stylizing the TextView in CardView
        if(diffMinutes > 60) {
            long diffHours = TimeUnit.MILLISECONDS.toHours(diff);
            if(diffHours>1) toolbar.setSubtitle(TimeUnit.MILLISECONDS.toHours(diff)+" hours ago");
            else toolbar.setSubtitle(TimeUnit.MILLISECONDS.toHours(diff)+" hour ago");
        }else{
            if(diffMinutes!=1) toolbar.setSubtitle(diffMinutes+" minutes ago");
            else toolbar.setSubtitle(diffMinutes+" minute ago");
        }
    }
}