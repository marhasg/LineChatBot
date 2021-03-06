package com.dicoding.linebotjava.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.Multicast;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.*;
import com.linecorp.bot.model.message.AudioMessage;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.objectmapper.ModelObjectMapper;
import com.linecorp.bot.model.profile.UserProfileResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static jdk.nashorn.internal.objects.NativeArray.push;

@RestController
public class Controller {

    @Autowired
    @Qualifier("lineMessagingClient")
    private LineMessagingClient lineMessagingClient;

    @Autowired
    @Qualifier("lineSignatureValidator")
    private LineSignatureValidator lineSignatureValidator;

    @RequestMapping(value = "/webhook", method = RequestMethod.POST)
    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String xLineSignature,
            @RequestBody String eventsPayload)
    {
        try {
            if (!lineSignatureValidator.validateSignature(eventsPayload.getBytes(), xLineSignature)){
                throw new RuntimeException("Invalid Signature Validation");
            }
            //parsing event
            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            EventsModel eventsModel = objectMapper.readValue(eventsPayload, EventsModel.class);

            eventsModel.getEvents().forEach((event)->{
                //kode replay message disini
                if (event instanceof MessageEvent){
                    if (((MessageEvent) event).getMessage() instanceof AudioMessageContent
                        || ((MessageEvent) event).getMessage() instanceof ImageMessageContent
                        || ((MessageEvent) event).getMessage() instanceof VideoMessageContent
                        || ((MessageEvent) event).getMessage() instanceof FileMessageContent
                    ){
                        String baseURL = "https://margoollabot.herokuapp.com";
                        String contentURL = baseURL+"/content/"+((MessageEvent)event).getMessage().getId();
                        String contentType = ((MessageEvent)event).getMessage().getClass().getSimpleName();
                        String textMsg = contentType.substring(0, contentType.length() -14)
                                +"yang kamu kirim bisa diakses dari link:\n"
                                +contentURL;

                        replyText(((MessageEvent)event).getReplyToken(), textMsg);
                    } else {
                        MessageEvent messageEvent = (MessageEvent) event;
                        TextMessageContent textMessageContent = (TextMessageContent) messageEvent.getMessage();
                        replyText(messageEvent.getReplyToken(), textMessageContent.getText());
                        replyText(messageEvent.getReplyToken(), "Ini Pesan Balasan");
                        replySticker(messageEvent.getReplyToken(), "11539", "52114119");
                    }
                }
            });
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (IOException e)
        {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
    private void reply(ReplyMessage replyMessage){
        try {
            lineMessagingClient.replyMessage(replyMessage).get();
        }catch (InterruptedException | ExecutionException e){
            throw new RuntimeException(e);
        }
    }

    private void replyText(String replyToken, String messageToUser){
        TextMessage textMessage = new TextMessage(messageToUser);
        ReplyMessage replyMessage = new ReplyMessage(replyToken, textMessage);
        reply(replyMessage);
    }
   private void replySticker(String replyToken, String packageId, String stickerId){
        StickerMessage stickerMessage = new StickerMessage(packageId, stickerId);
        ReplyMessage replyMessage = new ReplyMessage(replyToken, stickerMessage);
        reply(replyMessage);
    }

    @RequestMapping(value = "/pushmessage/{id}/{message}", method = RequestMethod.GET)
    public ResponseEntity<String> pushmessage(
            @PathVariable("id") String userId,
            @PathVariable("message") String textMsg
    ){
        TextMessage textMessage = new TextMessage(textMsg);
        PushMessage pushMessage = new PushMessage(userId, textMessage);
        push(pushMessage);
        StickerMessage stickerMessage = new StickerMessage("11539","52114130");
        PushMessage pushMessage1 = new PushMessage(userId, stickerMessage);
        push(pushMessage1);

        return new ResponseEntity<String>("Push message:"+textMsg+"\nsent to:"+userId, HttpStatus.OK);
    }

    private void push(PushMessage pushMessage){
        try {
            lineMessagingClient.pushMessage(pushMessage).get();
        } catch (InterruptedException | ExecutionException e){
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/multicast", method = RequestMethod.GET)
    public ResponseEntity<String> multicast(){
        String[] userIdList = {
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"};

        Set<String> listUsers = new HashSet<String>(Arrays.asList(userIdList));
        if (listUsers.size() > 0){
            String textMsg = "Ini pesan Multicast";
            sendMulticast(listUsers, textMsg);
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    private void sendMulticast(Set<String> listUsers, String textMsg) {
        TextMessage message = new TextMessage(textMsg);
        Multicast multicast = new Multicast(listUsers, message);

        try {
            lineMessagingClient.multicast(multicast).get();
        } catch (InterruptedException | ExecutionException e){
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/profile", method = RequestMethod.GET)
    public ResponseEntity<String> profile(
//            @PathVariable("id") String userId //
//            jika ingin userId dapat dimasukan secara manual
    ){
        String userId = "Isi dengan userId anda";
        UserProfileResponse profile = getProfile(userId);

        if (profile != null){
            String profileName = profile.getDisplayName();
            TextMessage textMessage = new TextMessage("Hello, "+ profileName);
            PushMessage pushMessage = new PushMessage(userId, textMessage);
            push(pushMessage);

            return new ResponseEntity<String>("Hello, "+profileName, HttpStatus.OK);
        }
        return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    }

    private UserProfileResponse getProfile(String userId) {
        try {
            return lineMessagingClient.getProfile(userId).get();
        } catch (InterruptedException | ExecutionException e){
            throw new RuntimeException(e);
        }
    }

    @RequestMapping(value = "/content/{id}", method = RequestMethod.GET)
    public ResponseEntity content(
            @PathVariable("id") String messageId
    ){
        MessageContentResponse messageContent = getContent(messageId);

        if (messageContent !=null){
            HttpHeaders headers = new HttpHeaders();
            String[] mimeType = messageContent.getMimeType().split("/");
            headers.setContentType(new MediaType(mimeType[0], mimeType[1]));

            InputStream inputStream = messageContent.getStream();
            InputStreamResource inputStreamResource = new InputStreamResource(inputStream);

            return new ResponseEntity<>(inputStream, headers, HttpStatus.OK);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    private MessageContentResponse getContent(String messageId) {
        try {
            return lineMessagingClient.getMessageContent(messageId).get();
        } catch (InterruptedException | ExecutionException e){
            throw  new RuntimeException(e);
        }
    }
}
