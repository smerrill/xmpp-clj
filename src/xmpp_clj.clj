(ns xmpp-clj
  (:import [org.jivesoftware.smack 
	    Chat 
	    ChatManager 
	    ConnectionConfiguration 
	    MessageListener
	    SASLAuthentication
	    XMPPConnection
	    XMPPException
	    PacketListener]
	   [org.jivesoftware.smack.packet Message Presence Presence$Type Message$Type]
	   [org.jivesoftware.smack.filter MessageTypeFilter]
	   [org.jivesoftware.smackx.muc MultiUserChat]
	   [org.jivesoftware.smack.util StringUtils]))

(def available-presence (Presence. Presence$Type/available))

(def chat-message-type-filter (MessageTypeFilter. Message$Type/groupchat))

(defn packet-listener [conn processor]
  (proxy 
      [PacketListener] 
      []
    (processPacket [packet] (processor conn packet))))

(defn mapify-error [e]
  (if (nil? e) 
    nil
    {:code (.getCode e) :message (.getMessage e)}))

(defn mapify-message [#^Message m]
  (try
   {:body (.getBody m)
    :subject (.getSubject m)
    :thread (.getThread m)
    :from (.getFrom m)
    :from-name (StringUtils/parseBareAddress (.getFrom m))
    :to (.getTo m)
    :packet-id (.getPacketID m)
    :error (mapify-error (.getError m))
    :type (keyword (str (.getType m)))}
   (catch Exception e (println e) {})))

(defn parse-address [from]
  (try
   (first (.split from "/"))
   (catch Exception e (println e) from)))

(defn create-reply [from-message-map to-message-body]
  (try
   (let [to (:from from-message-map)
	 rep (Message.)]
     (.setTo rep to)
     (.setBody rep (str to-message-body))
     rep)
   (catch Exception e (println e))))

(defn reply [from-message-map to-message-body conn]
  (.sendPacket conn (create-reply from-message-map to-message-body)))

(defn with-message-map [handler]
  (fn [conn packet]
    (let [message (mapify-message #^Message packet)]
      (try
       (handler conn message)
       (catch Exception e (println e))))))

(defn wrap-responder [handler]
  (fn [conn message]
    (let [resp (handler message)]
      (reply message resp conn))))

(defn join-room [conn room-name nick]
  (let [chat-room (MultiUserChat. conn room-name)]
    (.join chat-room nick)))

(defn start-bot
  "Defines and starts an instant messaging bot that will respond to incoming
   messages. start-bot takes 2 parameters, the first is a map representing 
   the data needed to make a connection to the jabber server:
   
   connnect-info example:
   {:host \"talk.google.com\"
    :domain \"gmail.com\"
    :username \"testclojurebot@gmail.com\"
    :password \"clojurebot12345\"}

   The second parameter expects a single-arg function, which is passed
   a map representing a message on receive. Return a string from this
   function to pass a message back to the sender, or nil for no
   response

   received message map example (nils are possible where n/a):
   {:body
    :subject
    :thread <Id used to correlate several messages, such as a converation>
    :from <entire from id, ex. zachary.kim@gmail.com/Zachary KiE0124793>
    :from-name <Just the 'name' from the 'from', ex. zachary.kim@gmail.com>
    :to <To whom the message was sent, i.e. this bot>
    :packet-id <donno>
    :error <a map representing the error, if present>
    :type <Type of message: normal, chat, group_chat, headline, error.
           see javadoc for org.jivesoftware.smack.packet.Message>}
   "
  [connect-info packet-processor]
  (let [un (:username connect-info)
	pw (:password connect-info)
	host (:host connect-info)
	domain (:domain connect-info)
        port (get connect-info :port 5222)
        rooms (:rooms connect-info)
	connect-config (ConnectionConfiguration. host port domain)
	conn (XMPPConnection. connect-config)]
    (.connect conn)
    (.login conn un pw)
    (.sendPacket conn available-presence)
    (if (vector? rooms)
      (apply #(join-room conn % un) rooms))
    (.addPacketListener
     conn
     (packet-listener conn (with-message-map (wrap-responder packet-processor)))
     chat-message-type-filter)
    conn))

(defn stop-bot [#^XMPPConnection conn]
  (.disconnect conn))
