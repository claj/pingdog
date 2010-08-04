;almost everything is stolen from http://travis-whitton.blogspot.com/2009/07/network-sweeping-with-clojure.html
;/Linus

; Travis Whitton: Kicking things off with a bit of ceremony, we'll import the required objects.
(import '(java.io IOException)
        '(java.net Socket)
        '(java.net InetSocketAddress)
        '(java.net SocketTimeoutException)
        '(java.net UnknownHostException))
        
;Travis Whitton: With that out of the way, we'll create a function to see if a given host / port combination is connectable. To avoid indefinite blocking, we'll make it so the connection can timeout (thanks to nikkomega from reddit for helping me improve this function).

(defn host-up? [hostname timeout port]
  (let [sock-addr (InetSocketAddress. hostname port)]
    (try
     (with-open [sock (Socket.)]
       (. sock connect sock-addr timeout)
       hostname)
     (catch IOException e false)
     (catch SocketTimeoutException e false)
     (catch UnknownHostException e false))))
     
;Travis Whitton: As you can see, the use of with-open ensures that the connection is closed regardless of the outcome. Any exceptions that may occur result in a return value of false. We'll use this later to filter through the relevant results. To avoid ruining the flexibility of the host-up? function, we'll add a second function to test specifically for ssh servers running on port 22.

(defn ssh-host-up? [hostname]
      (host-up? hostname 5000 22))
      
;Travis Whitton:The timeout is hardcoded at 5000 milliseconds, which is probably much longer than needed. Performance will suffer in a single-threaded application, but we'll address this later. With the hard work out of the way, we'll simply apply the functions to the desired data.

def network "192.168.1.")
; scan 192.168.1.1 - 192.168.1.254
(def ip-list (for [x (range 1 255)] (str network x)))
(doseq [host (filter ssh-host-up? ip-list)]
       (println (str host " is up")))
       
;Travis Whitton: After running this, I was able to retrieve the desired results and locate my machine; however, it took over twelve minutes to sweep the entire network. This is due to the long timeout and the fact that we're testing each host in a serial fashion. Seeing as we're using Clojure, a few small changes should improve the situation dramatically



;Travis Whitton: There are varying ways to add concurrency to a Clojure app, but agents provide a send-off function specifically designed for blocking tasks. Given the fact that we're sitting around waiting for most of these hosts to timeout, agents are a logical choice in this case. Since the first part of our program was written in a generic fashion, all we need to change is the application of the functions.

(def network "192.168.1.")
; scan 192.168.1.1 - 192.168.1.254
(def ip-list (for [x (range 1 255)] (str network x)))
(def agents (for [ip ip-list] (agent ip)))
 
(doseq [agent agents]
  (send-off agent ssh-host-up?))
 
(apply await agents)
 
(doseq [host (filter deref agents)]
  (println (str @host " is up")))
 
(shutdown-agents)