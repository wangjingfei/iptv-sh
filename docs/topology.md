# Topology

```text
                         regular Internet WAN
                                  |
                       GL-MT6000 / ImmortalWrt
                       LAN 192.168.31.1/24
                      /                     \
             main LAN / Wi-Fi                LAN5.85 tagged
                  |                                 |
           FnOS NAS .200                   optical modem IPTV
                  |
       +----------+-----------+
       |                      |
 iptv-hls :8090          iptv-epg :8091
       |                      | playlist + XMLTV
       +----------+-----------+
                  |
       udpxy 192.168.31.1:4022
                  |
       IPTV multicast on VLAN 85

 LAN4 hybrid port:
   untagged -> br-stb 192.168.32.0/24 -> ZTE STB normal network + Option 125
   tagged 85 -> lan4.85 -> br-iptv -> lan5.85 -> ZTE STB operator IPTV network
```

## Playback Flows

### NAS HLS playback

`TV/player -> NAS:8090/{channel-id}/index.m3u8 -> FFmpeg stream copy -> router:4022/rtp/<group>:<port>/ -> VLAN 85 multicast`

FFmpeg starts only when the channel is requested and writes temporary HLS segments to container tmpfs. The configured channel sources should be reviewed before copying to another network.

### EPG-enabled playback

`TV/player -> NAS:8091/playlist.m3u -> NAS:8091/epg.xml for guide data`

Each playlist video URL points back to NAS:8090. The EPG service does not relay media and should not add meaningful load during playback.

### ZTE set-top box

The ZTE box receives a local lease on the untagged path and its operator VLAN 85 path is bridged at layer 2. IPTV broadcast traffic remains on the IPTV VLAN and is not routed into the main LAN.
