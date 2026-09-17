import { describe, expect, it } from 'vitest'
import { splitFrames } from '../src/api/sse'

describe('SSE 帧切分（splitFrames）', () => {
  it('标准帧：event + data + 空行', () => {
    const { frames, rest } = splitFrames('event: delta\ndata: {"content":"你"}\n\n')
    expect(frames).toEqual([{ event: 'delta', data: '{"content":"你"}' }])
    expect(rest).toBe('')
  })

  it('一次多块：连续两帧', () => {
    const buf = 'event: delta\ndata: {"content":"你"}\n\nevent: done\ndata: {"ok":1}\n\n'
    const { frames, rest } = splitFrames(buf)
    expect(frames).toHaveLength(2)
    expect(frames[1].event).toBe('done')
    expect(rest).toBe('')
  })

  it('跨包半帧：残帧保留到下一次', () => {
    const first = splitFrames('event: delta\ndata: {"cont')
    expect(first.frames).toHaveLength(0)
    expect(first.rest).toBe('event: delta\ndata: {"cont')

    const second = splitFrames(first.rest + 'ent":"好"}\n\n')
    expect(second.frames).toHaveLength(1)
    expect(second.frames[0].data).toBe('{"content":"好"}')
  })

  it('兼容 \\r\\n 换行', () => {
    const { frames } = splitFrames('event: error\r\ndata: {"code":503,"message":"x"}\r\n\r\n')
    expect(frames).toEqual([{ event: 'error', data: '{"code":503,"message":"x"}' }])
  })

  it('多行 data 以 \\n 拼接；无 data 的帧被丢弃', () => {
    const { frames } = splitFrames('event: delta\ndata: a\ndata: b\n\nevent: ping\n\n')
    expect(frames).toEqual([{ event: 'delta', data: 'a\nb' }])
  })
})
