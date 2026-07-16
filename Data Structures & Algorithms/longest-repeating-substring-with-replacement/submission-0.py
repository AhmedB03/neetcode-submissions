class Solution:
    def characterReplacement(self, s: str, k: int) -> int:
        freq = {}
        l = 0
        longest =0
        max_freq = 0

        for r in range(len(s)):
            freq[s[r]] = freq.get(s[r],0) + 1
            max_freq = max(max_freq,freq[s[r]])
            window_lenght = r-l + 1
            print(freq.items())
            if window_lenght - max_freq > k:
                freq[s[l]] -= 1
                l += 1
            longest = max(longest,r-l+1)
        print(freq.items())
        return longest



        